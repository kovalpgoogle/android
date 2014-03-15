/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.idea.editors.navigation;

import com.android.navigation.Dimension;
import com.android.navigation.*;
import com.android.tools.idea.actions.AndroidShowNavigationEditor;
import com.android.tools.idea.editors.navigation.macros.Analyser;
import com.android.tools.idea.editors.navigation.macros.CodeGenerator;
import com.intellij.AppTopics;
import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileAdapter;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SideBorder;
import com.intellij.ui.components.JBScrollPane;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeListener;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.List;

import static com.android.tools.idea.editors.navigation.NavigationView.GAP;

@SuppressWarnings("UseOfSystemOutOrSystemErr")
public class NavigationEditor implements FileEditor {
  public static final String TOOLBAR = "NavigationEditorToolbar";
  private static final Logger LOG = Logger.getInstance("#" + NavigationEditor.class.getName());
  public static final boolean DEBUG = false;
  private static final String NAME = "Navigation";
  private static final int INITIAL_FILE_BUFFER_SIZE = 1000;
  private static final int SCROLL_UNIT_INCREMENT = 20;

  private final UserDataHolderBase myUserDataHolder = new UserDataHolderBase();
  private RenderingParameters myRenderingParams;
  private NavigationModel myNavigationModel;
  private final Listener<NavigationModel.Event> myNavigationModelListener;
  private final VirtualFile myFile;
  private JComponent myComponent;
  private boolean myDirty;
  private boolean myPendingFileSystemChanges;
  private boolean myNotificationsDisabled;
  private final CodeGenerator myCodeGenerator;
  private Analyser myAnalyser;
  private VirtualFileAdapter myVirtualFileListener;

  public NavigationEditor(Project project, VirtualFile file) {
    // Listen for 'Save All' events
    FileDocumentManagerListener saveListener = new FileDocumentManagerAdapter() {
      @Override
      public void beforeAllDocumentsSaving() {
        try {
          saveFile();
        }
        catch (IOException e) {
          LOG.error("Unexpected exception while saving navigation file", e);
        }
      }
    };
    project.getMessageBus().connect(this).subscribe(AppTopics.FILE_DOCUMENT_SYNC, saveListener);
    myFile = file;
    myRenderingParams = NavigationView.getRenderingParams(project, file);
    try {
      myNavigationModel = read(file);
      NavigationView editor = new NavigationView(myRenderingParams, myNavigationModel);
      JBScrollPane scrollPane = new JBScrollPane(editor);
      scrollPane.getVerticalScrollBar().setUnitIncrement(SCROLL_UNIT_INCREMENT);
      JPanel p = new JPanel(new BorderLayout());

      JComponent controls = createToolbar(editor);
      p.add(controls, BorderLayout.NORTH);
      p.add(scrollPane);
      myComponent = p;
    }
    catch (FileReadException e) {
      myNavigationModel = new NavigationModel();
      {
        JPanel panel = new JPanel(new BorderLayout());
        JLabel message = new JLabel("Invalid Navigation File");
        Font font = message.getFont();
        message.setFont(font.deriveFont(30f));
        panel.add(message, BorderLayout.NORTH);
        panel.add(new JLabel(e.getMessage()), BorderLayout.CENTER);
        myComponent = new JBScrollPane(panel);
        if (DEBUG) {
          e.printStackTrace();
        }
      }
    }
    Module module = myRenderingParams.myConfiguration.getModule();
    myAnalyser = new Analyser(project, module);
    myCodeGenerator = new CodeGenerator(myNavigationModel, module);
    myNavigationModelListener = new Listener<NavigationModel.Event>() {
      @Override
      public void notify(@NotNull NavigationModel.Event event) {
        if (!myNotificationsDisabled &&
            event.operation == NavigationModel.Event.Operation.INSERT &&
            event.operandType == Transition.class) {
          ArrayList<Transition> transitions = myNavigationModel.getTransitions();
          Transition transition = transitions.get(transitions.size() - 1); // todo don't rely on this being the last
          myCodeGenerator.implementTransition(transition);
        }
        myDirty = true;
      }
    };
    myNavigationModel.getListeners().add(myNavigationModelListener);
    myVirtualFileListener = new VirtualFileAdapter() {
      private void somethingChanged(String changeType, @NotNull VirtualFileEvent event) {
        if (DEBUG) {
          System.out.println(changeType + ": " + event);
          try {
            VirtualFile file1 = event.getFile();
            System.out.println("NavigationEditor: somethingChanged file: " + file1);
            System.out.println("NavigationEditor: contents: " + new String(file1.contentsToByteArray()));
          }
          catch (IOException e) {
            throw new RuntimeException(e);
          }
        }
          /*
          VirtualFileManager.getInstance().asyncRefresh(new Runnable() {
            @Override
            public void run() {
              updateNavigationModel();
            }
          });
          */
        // Batch up all the changes and post to the event queue to re-analyse when they're all
        if (!myPendingFileSystemChanges) {
          myPendingFileSystemChanges = true;
          SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
              updateNavigationModel();
              myPendingFileSystemChanges = false;
            }
          });
        }
      }

      @Override
      public void contentsChanged(@NotNull VirtualFileEvent event) {
        somethingChanged("contentsChanged", event);
      }

      @Override
      public void fileCreated(@NotNull VirtualFileEvent event) {
        somethingChanged("fileCreated", event);
      }

      @Override
      public void fileDeleted(@NotNull VirtualFileEvent event) {
        somethingChanged("fileDeleted", event);
      }
    };
  }


  // See  AndroidDesignerActionPanel

  protected JComponent createToolbar(NavigationView myDesigner) {
    JPanel panel = new JPanel(new BorderLayout());
    panel.setBorder(IdeBorderFactory.createBorder(SideBorder.BOTTOM));
    // the UI below is a temporary hack to show UX / dev. rel
    {
      final String dirName = myFile.getParent().getName();

      JPanel combos = new JPanel(new FlowLayout());
      //combos.add(new JLabel(dirName));
      {
        final String phone = "phone";
        final String phablet = "phablet";
        final String tablet = "tablet";
        final ComboBox deviceSelector = new ComboBox(new Object[]{phone, phablet, tablet});
        final String portrait = "portrait";
        final String landscape = "landscape";
        final ComboBox orientationSelector = new ComboBox(new Object[]{portrait, landscape});
        deviceSelector.setSelectedItem(dirName.contains("-sw600dp") ? tablet : phone);
        orientationSelector.setSelectedItem(dirName.contains("-land") ? landscape : portrait);
        ActionListener actionListener = new ActionListener() {
          boolean disabled = false;
          @Override
          public void actionPerformed(ActionEvent actionEvent) {
            if (disabled) {
              return;
            }
            Object device = deviceSelector.getSelectedItem();
            Object deviceQualifier = (device == tablet) ? "-sw600dp" : "";
            Object orientation = orientationSelector.getSelectedItem();
            Object orientationQualifier = (orientation == landscape) ? "-land" : "";
            new AndroidShowNavigationEditor().showNavigationEditor(myRenderingParams.myProject,
                                                                   "raw" + deviceQualifier + orientationQualifier, "main.nvg.xml");
            disabled = true;
            deviceSelector.setSelectedItem(dirName.contains("-sw600dp") ? tablet : phone);
            orientationSelector.setSelectedItem(dirName.contains("-land") ? landscape : portrait);
            disabled = false;

          }
        };
        {
          deviceSelector.addActionListener(actionListener);
          combos.add(deviceSelector);
        }
        {
          orientationSelector.addActionListener(actionListener);
          combos.add(orientationSelector);
        }
      }
      panel.add(combos, BorderLayout.CENTER);
    }

    {
      ActionManager actionManager = ActionManager.getInstance();
      ActionToolbar zoomToolBar = actionManager.createActionToolbar(TOOLBAR, getActions(myDesigner), true);
      panel.add(zoomToolBar.getComponent(), BorderLayout.EAST);
    }

    return panel;
  }

  private static class FileReadException extends Exception {
    private FileReadException(Throwable throwable) {
      super(throwable);
    }
  }

  // See AndroidDesignerActionPanel
  private static ActionGroup getActions(final NavigationView myDesigner) {
    DefaultActionGroup group = new DefaultActionGroup();

    group.add(new AnAction(null, "Zoom Out (-)", AndroidIcons.ZoomOut) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        myDesigner.zoom(false);
      }
    });
    group.add(new AnAction(null, "Reset Zoom to 100% (1)", AndroidIcons.ZoomActual) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        myDesigner.setScale(1);
      }
    });
    group.add(new AnAction(null, "Zoom In (+)", AndroidIcons.ZoomIn) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        myDesigner.zoom(true);
      }
    });

    return group;
  }

  private static NavigationModel read(VirtualFile file) throws FileReadException {
    try {
      InputStream inputStream = file.getInputStream();
      if (inputStream.available() == 0) {
        return new NavigationModel();
      }
      return (NavigationModel)new XMLReader(inputStream).read();
    }
    catch (Exception e) {
      throw new FileReadException(e);
    }
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myComponent;
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return null;
  }

  @NotNull
  @Override
  public String getName() {
    return NAME;
  }

  @NotNull
  @Override
  public FileEditorState getState(@NotNull FileEditorStateLevel level) {
    return FileEditorState.INSTANCE;
  }

  @Override
  public void setState(@NotNull FileEditorState state) {
  }

  @Override
  public boolean isModified() {
    return myDirty;
  }

  @Override
  public boolean isValid() {
    return myFile.isValid();
  }

  private void layoutStatesWithUnsetLocations(NavigationModel navigationModel) {
    Collection<State> states = navigationModel.getStates();
    final Map<State, com.android.navigation.Point> stateToLocation = navigationModel.getStateToLocation();
    final Set<State> visited = new HashSet<State>();
    Dimension size = myRenderingParams.getDeviceScreenSize();
    Dimension gridSize = new Dimension(size.width + GAP.width, size.height + GAP.height);
    final Point location = new Point(GAP.width, GAP.height);
    final int gridWidth = gridSize.width;
    final int gridHeight = gridSize.height;
    for (State state : states) {
      if (visited.contains(state)) {
        continue;
      }
      new Object() {
        public void addChildrenFor(State source) {
          visited.add(source);
          if (!stateToLocation.containsKey(source)) {
            stateToLocation.put(source, new com.android.navigation.Point(location.x, location.y));
          }
          List<State> children = findDestinationsFor(source, visited);
          location.x += gridWidth;
          if (children.isEmpty()) {
            location.y += gridHeight;
          }
          else {
            for (State child : children) {
              addChildrenFor(child);
            }
          }
          location.x -= gridWidth;
        }
      }.addChildrenFor(state);
    }
  }

  private List<State> findDestinationsFor(State source, Set<State> visited) {
    java.util.List<State> result = new ArrayList<State>();
    for (Transition transition : myNavigationModel.getTransitions()) {
      if (transition.getSource().getState() == source) {
        State destination = transition.getDestination().getState();
        if (!visited.contains(destination)) {
          result.add(destination);
        }
      }
    }
    return result;
  }

  private void updateNavigationModel() {
    if (myRenderingParams.myProject.isDisposed()) {
      return;
    }
    myNotificationsDisabled = true;
    myNavigationModel.clear();
    myNavigationModel.getTransitions().clear();
    myAnalyser.deriveAllStatesAndTransitions(myNavigationModel, myRenderingParams.myConfiguration);
    layoutStatesWithUnsetLocations(myNavigationModel);
    myNotificationsDisabled = false;
    myNavigationModel.getListeners().notify(NavigationModel.Event.update(Object.class));
  }

  @Override
  public void selectNotify() {
    updateNavigationModel();
    VirtualFileManager.getInstance().addVirtualFileListener(myVirtualFileListener);
  }

  @Override
  public void deselectNotify() {
    VirtualFileManager.getInstance().removeVirtualFileListener(myVirtualFileListener);
  }

  @Override
  public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {
  }

  @Override
  public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {
  }

  @Nullable
  @Override
  public BackgroundEditorHighlighter getBackgroundHighlighter() {
    return null;
  }

  @Nullable
  @Override
  public FileEditorLocation getCurrentLocation() {
    return null;
  }

  @Nullable
  @Override
  public StructureViewBuilder getStructureViewBuilder() {
    return null;
  }

  private void saveFile() throws IOException {
    if (myDirty) {
      ByteArrayOutputStream stream = new ByteArrayOutputStream(INITIAL_FILE_BUFFER_SIZE);
      new XMLWriter(stream).write(myNavigationModel);
      myFile.setBinaryContent(stream.toByteArray());
      myDirty = false;
    }
  }

  @Override
  public void dispose() {
    try {
      saveFile();
    }
    catch (IOException e) {
      LOG.error("Unexpected exception while saving navigation file", e);
    }

    myNavigationModel.getListeners().remove(myNavigationModelListener);
  }

  @Nullable
  @Override
  public <T> T getUserData(@NotNull Key<T> key) {
    return myUserDataHolder.getUserData(key);
  }

  @Override
  public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {
    myUserDataHolder.putUserData(key, value);
  }
}
