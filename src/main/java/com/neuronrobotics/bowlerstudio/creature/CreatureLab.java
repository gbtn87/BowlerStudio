package com.neuronrobotics.bowlerstudio.creature;

import com.neuronrobotics.bowlerstudio.BowlerStudio;
import com.neuronrobotics.bowlerstudio.BowlerStudioController;
import com.neuronrobotics.bowlerstudio.BowlerStudioModularFrame;
import com.neuronrobotics.bowlerstudio.assets.AssetFactory;
import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine;
import com.neuronrobotics.bowlerstudio.tabs.AbstractBowlerStudioTab;
import com.neuronrobotics.bowlerstudio.threed.MobileBaseCadManager;
import com.neuronrobotics.nrconsole.util.FileWatchDeviceWrapper;
import com.neuronrobotics.sdk.addons.gamepad.BowlerJInputDevice;
import com.neuronrobotics.sdk.addons.kinematics.DHParameterKinematics;
import com.neuronrobotics.sdk.addons.kinematics.DhInverseSolver;
import com.neuronrobotics.sdk.addons.kinematics.IDriveEngine;
import com.neuronrobotics.sdk.addons.kinematics.MobileBase;
import com.neuronrobotics.sdk.common.BowlerAbstractDevice;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXMLLoader;
import javafx.scene.Group;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

public class CreatureLab extends AbstractBowlerStudioTab implements IOnEngineeringUnitsChange {

	private BowlerAbstractDevice pm;

	private IDriveEngine defaultDriveEngine;
	// private DhInverseSolver defaultDHSolver;
	private Menu localMenue;
	private ProgressIndicator pi;

	private MobileBaseCadManager baseManager;
	private CheckBox autoRegen = new CheckBox("Auto-Regnerate CAD");

	Parent root;
	private BowlerJInputDevice gameController = null;
	CreatureLabControlsTab tab = new CreatureLabControlsTab();;

	@Override
	public void onTabClosing() {
		baseManager.onTabClosing();
	}

	@Override
	public String[] getMyNameSpaces() {
		// TODO Auto-generated method stub
		return new String[0];
	}

	@SuppressWarnings({ "restriction", "restriction" })
	@Override
	public void initializeUI(BowlerAbstractDevice pm) {
		setGraphic(AssetFactory.loadIcon("CreatureLab-Tab.png"));
		this.pm = pm;
		autoRegen.setSelected(true);
		autoRegen.setOnAction(event -> {
			if (autoRegen.isSelected()) {
				generateCad();
			}
		});
		// TODO Auto-generated method stub
		setText(pm.getScriptingName());

		try {
			ScriptingEngine.setAutoupdate(true);
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		MobileBase device = (MobileBase) pm;

		// Button save = new Button("Save Configuration");

		setDefaultWalkingEngine(device);

		FXMLLoader loader;
		try {
			loader = AssetFactory.loadLayout("layout/CreatureLabControlsTab.fxml", true);
			Platform.runLater(() -> {
				loader.setController(tab);
				// This is needed when loading on MAC
				loader.setClassLoader(getClass().getClassLoader());
				try {
					root = loader.load();
					finishLoading(device);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			});

		} catch (Exception e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

	}

	private void finishLoading(MobileBase device) {

		TreeView<String> tree =null;
		TreeItem<String> rootItem =null;

	
		try {
			rootItem = new TreeItem<String>( device.getScriptingName(),AssetFactory.loadIcon("creature.png"));
		} catch (Exception e) {
			rootItem = new TreeItem<String>( device.getScriptingName());
		}
		tree= new TreeView<>(rootItem);
		AnchorPane treebox= tab. getTreeBox();
		treebox.getChildren().clear();
		treebox.getChildren().add(tree);
		AnchorPane.setTopAnchor(tree, 0.0);
		AnchorPane.setLeftAnchor(tree, 0.0);
     	AnchorPane.setRightAnchor(tree, 0.0);
     	AnchorPane.setBottomAnchor(tree, 0.0);
		
		rootItem.setExpanded(true);
		HashMap<TreeItem<String>, Runnable> callbackMapForTreeitems = new HashMap<>();
		HashMap<TreeItem<String>, Group> widgetMapForTreeitems = new HashMap<>();

		try {
			MobleBaseMenueFactory.load(device, tree, rootItem, callbackMapForTreeitems, widgetMapForTreeitems, this);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		tree.setPrefWidth(325);
		tree.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
		JogWidget walkWidget = new JogWidget(device);
		tree.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<Object>() {

			@Override
			public void changed(ObservableValue<?> observable, Object oldValue, Object newValue) {
				@SuppressWarnings("unchecked")
				TreeItem<String> treeItem = (TreeItem<String>) newValue;
				new Thread() {
					public void run() {
						if (walkWidget.getGameController() != null)
							setGameController(walkWidget.getGameController());
						if (callbackMapForTreeitems.get(treeItem) != null) {
							callbackMapForTreeitems.get(treeItem).run();
						}
						if (widgetMapForTreeitems.get(treeItem) != null) {

							Platform.runLater(() -> {
								tab.getControlsBox().getChildren().clear();
								Group g = widgetMapForTreeitems.get(treeItem);
								tab.getControlsBox().getChildren().add(g);
								AnchorPane.setTopAnchor(g, 0.0);
								AnchorPane.setLeftAnchor(g, 0.0);
						     	AnchorPane.setRightAnchor(g, 0.0);
						     	AnchorPane.setBottomAnchor(g, 0.0);
							});
						} else {
							Platform.runLater(() -> {
								tab.getControlsBox().getChildren().clear();
							});
							BowlerStudio.select(device);
							walkWidget.setGameController(getController());
						}
					}
				}.start();

			}
		});

		HBox progress = new HBox(10);
		pi = new ProgressIndicator(0);
		progress.getChildren().addAll(new Label("Cad Progress:"), pi, autoRegen);
		baseManager = new MobileBaseCadManager(device, pi, autoRegen);

		progress.setStyle("-fx-background-color: #FFFFFF;");
		progress.setOpacity(.7);
		progress.setPrefSize(325, 50);
		tab.setOverlayTop(progress);
		tab.setOverlayTopRight(walkWidget);

		BowlerStudioModularFrame.getBowlerStudioModularFrame().showCreatureLab();

		generateCad();

		setContent(root);

	}

	private File setDefaultDhParameterKinematics(DHParameterKinematics device) {
		File code = null;
		try {
			code = ScriptingEngine.fileFromGit(device.getGitDhEngine()[0], device.getGitDhEngine()[1]);
			DhInverseSolver defaultDHSolver = (DhInverseSolver) ScriptingEngine.inlineFileScriptRun(code, null);

			File c = code;
			FileWatchDeviceWrapper.watch(device, code, (fileThatChanged, event) -> {

				try {
					System.out.println("D-H Solver changed, updating " + device.getScriptingName());
					DhInverseSolver d = (DhInverseSolver) ScriptingEngine.inlineFileScriptRun(c, null);
					device.setInverseSolver(d);
				} catch (Exception ex) {
					BowlerStudioController.highlightException(c, ex);
				}
			});

			device.setInverseSolver(defaultDHSolver);
			return code;
		} catch (Exception e1) {
			BowlerStudioController.highlightException(code, e1);
		}
		return null;

	}

	private void setDefaultWalkingEngine(MobileBase device) {
		if (defaultDriveEngine == null) {
			setGitWalkingEngine(device.getGitWalkingEngine()[0], device.getGitWalkingEngine()[1], device);
		}
		for (DHParameterKinematics dh : device.getAllDHChains()) {
			setDefaultDhParameterKinematics(dh);
		}
	}

	public void setGitWalkingEngine(String git, String file, MobileBase device) {

		device.setGitWalkingEngine(new String[] { git, file });
		File code = null;
		try {
			code = ScriptingEngine.fileFromGit(git, file);
		} catch (GitAPIException | IOException e) {
			BowlerStudioController.highlightException(code, e);
		}

		File c = code;
		FileWatchDeviceWrapper.watch(device, code, (fileThatChanged, event) -> {

			try {

				defaultDriveEngine = (IDriveEngine) ScriptingEngine.inlineFileScriptRun(c, null);
				device.setWalkingDriveEngine(defaultDriveEngine);
			} catch (Exception ex) {
				BowlerStudioController.highlightException(c, ex);
			}

		});

		try {
			defaultDriveEngine = (IDriveEngine) ScriptingEngine.inlineFileScriptRun(c, null);
			device.setWalkingDriveEngine(defaultDriveEngine);
		} catch (Exception ex) {
			BowlerStudioController.highlightException(c, ex);
		}
	}

	public void generateCad() {
		// new Exception().printStackTrace();
		baseManager.generateCad();
	}

	@Override
	public void onTabReOpening() {
		baseManager.setCadScript(baseManager.getCadScript());
		try {
			if (autoRegen.isSelected())
				generateCad();
		} catch (Exception ex) {

		}
	}

	public static String getFormatted(double value) {
		return String.format("%4.3f%n", (double) value);
	}

	@Override
	public void onSliderMoving(EngineeringUnitsSliderWidget source, double newAngleDegrees) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onSliderDoneMoving(EngineeringUnitsSliderWidget source, double newAngleDegrees) {
		if (autoRegen.isSelected())
			generateCad();
	}

	public BowlerJInputDevice getController() {

		return getGameController();
	}

	public void setGitDhEngine(String gitsId, String file, DHParameterKinematics dh) {
		dh.setGitDhEngine(new String[] { gitsId, file });

		setDefaultDhParameterKinematics(dh);

	}

	public void setGitCadEngine(String gitsId, String file, MobileBase device)
			throws InvalidRemoteException, TransportException, GitAPIException, IOException {
		baseManager.setGitCadEngine(gitsId, file, device);
	}

	public void setGitCadEngine(String gitsId, String file, DHParameterKinematics dh)
			throws InvalidRemoteException, TransportException, GitAPIException, IOException {
		baseManager.setGitCadEngine(gitsId, file, dh);
	}

	public BowlerJInputDevice getGameController() {
		return gameController;
	}

	public void setGameController(BowlerJInputDevice bowlerJInputDevice) {
		this.gameController = bowlerJInputDevice;
	}

}
