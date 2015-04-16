/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.neuronrobotics.bowlerstudio;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.MeshContainer;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.lang.Script;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.WatchEvent;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Bounds;
import javafx.scene.Group;
import javafx.scene.PerspectiveCamera;
import javafx.scene.SceneAntialiasing;
import javafx.scene.SnapshotParameters;
import javafx.scene.SubScene;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.Sphere;
import javafx.scene.transform.Affine;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Scale;
import javafx.scene.transform.Transform;
import javafx.stage.FileChooser;

import javax.imageio.ImageIO;
import javax.usb.UsbDevice;

import org.apache.commons.io.IOUtils;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ImportCustomizer;
import org.controlsfx.control.action.Action;
import org.controlsfx.dialog.Dialogs;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.StyleSpansBuilder;
import org.reactfx.Change;
import org.reactfx.EventStream;
import org.reactfx.EventStreams;

import com.neuronrobotics.bowlerstudio.tabs.ScriptingEngineWidget;
import com.neuronrobotics.interaction.CadInteractionEvent;
import com.neuronrobotics.sdk.addons.kinematics.AbstractKinematicsNR;
import com.neuronrobotics.sdk.addons.kinematics.DHParameterKinematics;
import com.neuronrobotics.sdk.addons.kinematics.ITaskSpaceUpdateListenerNR;
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;
import com.neuronrobotics.sdk.common.Log;
import com.neuronrobotics.sdk.dyio.DyIO;
import com.neuronrobotics.sdk.dyio.dypid.DyPIDConfiguration;
import com.neuronrobotics.sdk.dyio.peripherals.DigitalInputChannel;
import com.neuronrobotics.sdk.javaxusb.UsbCDCSerialConnection;
import com.neuronrobotics.sdk.pid.PIDConfiguration;
import com.neuronrobotics.sdk.serial.SerialConnection;
import com.neuronrobotics.sdk.ui.ConnectionDialog;
import com.neuronrobotics.sdk.util.FileChangeWatcher;
import com.neuronrobotics.sdk.util.IFileChangeListener;
import com.neuronrobotics.sdk.util.ThreadUtil;
import com.neuronrobotics.sdk.addons.kinematics.gui.*;
/**
 * FXML Controller class
 *
 * @author Michael Hoffer &lt;info@michaelhoffer.de&gt;
 */
public class MainController implements Initializable, IFileChangeListener {

    private static final String[] KEYWORDS = new String[]{
        "def", "in", "as", "abstract", "assert", "boolean", "break", "byte",
        "case", "catch", "char", "class", "const",
        "continue", "default", "do", "double", "else",
        "enum", "extends", "final", "finally", "float",
        "for", "goto", "if", "implements", "import",
        "instanceof", "int", "interface", "long", "native",
        "new", "package", "private", "protected", "public",
        "return", "short", "static", "strictfp", "super",
        "switch", "synchronized", "this", "throw", "throws",
        "transient", "try", "void", "volatile", "while"
    };

    private static final Pattern KEYWORD_PATTERN
            = Pattern.compile("\\b(" + String.join("|", KEYWORDS) + ")\\b");
    
	static ByteArrayOutputStream out = new ByteArrayOutputStream();
	
	static{
        System.setOut(new PrintStream(out));
		Platform.runLater(() -> {
			handlePrintUpdate();
		});
	}
	
	static void handlePrintUpdate() {

		ThreadUtil.wait(200);
		Platform.runLater(() -> {
			if(out.size()>0){
				Platform.runLater(() -> {
					String newString = out.toString();
					out.reset();
					if(logView!=null){
						String current = logView.getText()+newString;
						if(current.getBytes().length>2000)
							current=new String(current.substring(current.getBytes().length-2000));
						final String toSet=current;
						logView.setText(toSet);
						logView.setScrollTop(Double.MAX_VALUE);	
					}
				});
			}
		});
		Platform.runLater(() -> {
			// TODO Auto-generated method stub
			handlePrintUpdate();
		});
	}



    private final CodeArea codeArea = new CodeArea();

    private boolean autoCompile = true;

    private CSG csgObject;

    @FXML
    private static TextArea logView;

    @FXML
    private ScrollPane editorContainer;

    @FXML
    private Pane viewContainer;

    private SubScene subScene;
    private Jfx3dManager jfx3dmanager ;

	private File openFile;

	private FileChangeWatcher watcher;


	private MeshContainer meshContainer;

	private MeshView meshView;

	private BowlerStudioController application;
	
    /**
     * Initializes the controller class.
     *
     * @param url
     * @param rb
     */
    @Override
    public void initialize(URL url, ResourceBundle rb) {

        //
        codeArea.textProperty().addListener(
                (ov, oldText, newText) -> {
                    Matcher matcher = KEYWORD_PATTERN.matcher(newText);
                    int lastKwEnd = 0;
                    StyleSpansBuilder<Collection<String>> spansBuilder
                    = new StyleSpansBuilder<>();
                    while (matcher.find()) {
                        spansBuilder.add(Collections.emptyList(),
                                matcher.start() - lastKwEnd);
                        spansBuilder.add(Collections.singleton("keyword"),
                                matcher.end() - matcher.start());
                        lastKwEnd = matcher.end();
                    }
                    spansBuilder.add(Collections.emptyList(),
                            newText.length() - lastKwEnd);
                    codeArea.setStyleSpans(0, spansBuilder.create());
                });

        EventStream<Change<String>> textEvents
                = EventStreams.changesOf(codeArea.textProperty());

        textEvents.reduceSuccessions((a, b) -> b, Duration.ofMillis(500)).
                subscribe(code -> {
                    if (autoCompile) {
                        compile(code.getNewValue());
                    }
                });

        codeArea.replaceText(
                "\n"
                + "CSG cube = new Cube(20).toCSG()\n"
                + "CSG sphere = new Sphere(12.5).toCSG()\n"
                + "\n"
                + "cube.difference(sphere)");

        application = new BowlerStudioController();
        editorContainer.setContent(application);
        
        jfx3dmanager = new Jfx3dManager();
        subScene = jfx3dmanager.getSubScene();
        subScene.widthProperty().bind(viewContainer.widthProperty());
        subScene.heightProperty().bind(viewContainer.heightProperty());

        viewContainer.getChildren().add(subScene);

        System.out.println("Starting Application");
    }

 

	private void setCode(String code) {
        codeArea.replaceText(code);
    }

    private String getCode() {
        return codeArea.getText();
    }

    private void clearLog() {
        logView.setText("");
    }

    private void compile(String code) {

        csgObject = null;

        //clearLog();
        
        StringWriter sw = new StringWriter();
    	PrintWriter pw = new PrintWriter(sw);
    	ByteArrayOutputStream out = new ByteArrayOutputStream();
    	
        try {

            CompilerConfiguration cc = new CompilerConfiguration();

            cc.addCompilationCustomizers(
                    new ImportCustomizer().
                    addStarImports("eu.mihosoft.vrl.v3d",
                            "eu.mihosoft.vrl.v3d.samples").
                    addStaticStars("eu.mihosoft.vrl.v3d.Transform"));
            
            cc.addCompilationCustomizers(
                    new ImportCustomizer().
                    addStarImports("com.neuronrobotics.sdk.dyio",
                            "com.neuronrobotics.sdk.common"));
        	
            Binding binding = new Binding();
            System.setOut(new PrintStream(out));

            GroovyShell shell = new GroovyShell(getClass().getClassLoader(),
            		binding, cc);

            Script script = shell.parse(code);
 
            Object obj = script.run();
            
            
            if (obj instanceof CSG) {
            	
                CSG csg = (CSG) obj;

                csgObject = csg;
                //CadInteractionEvent interact =new CadInteractionEvent();
                
                meshContainer = csg.toJavaFXMesh(null);

                meshView = jfx3dmanager.replaceObject(meshView, meshContainer.getAsMeshViews().get(0));

            }
        } catch (Throwable ex) {
        	ex.printStackTrace(pw);
        
        }
      	
    }

    /**
     * Returns the location of the Jar archive or .class file the specified
     * class has been loaded from. <b>Note:</b> this only works if the class is
     * loaded from a jar archive or a .class file on the locale file system.
     *
     * @param cls class to locate
     * @return the location of the Jar archive the specified class comes from
     */
    public static File getClassLocation(Class<?> cls) {

//        VParamUtil.throwIfNull(cls);
        String className = cls.getName();
        ClassLoader cl = cls.getClassLoader();
        URL url = cl.getResource(className.replace(".", "/") + ".class");

        String urlString = url.toString().replace("jar:", "");

        if (!urlString.startsWith("file:")) {
            throw new IllegalArgumentException("The specified class\""
                    + cls.getName() + "\" has not been loaded from a location"
                    + "on the local filesystem.");
        }

        urlString = urlString.replace("file:", "");
        urlString = urlString.replace("%20", " ");

        int location = urlString.indexOf(".jar!");

        if (location > 0) {
            urlString = urlString.substring(0, location) + ".jar";
        } else {
            //System.err.println("No Jar File found: " + cls.getName());
        }

        return new File(urlString);
    }
    
    @FXML
    private void onLoadFile(ActionEvent e) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open JFXScad File");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(
                        "JFXScad files (*.jfxscad, *.groovy)",
                        "*.jfxscad", "*.groovy"));
        

        openFile = fileChooser.showOpenDialog(null);

        if (openFile == null) {
            return;
        }

        String fName = openFile.getAbsolutePath();

        if (!fName.toLowerCase().endsWith(".groovy")
                && !fName.toLowerCase().endsWith(".jfxscad")) {
            fName += ".jfxscad";
        }

        try {
            setCode(new String(Files.readAllBytes(Paths.get(fName)), "UTF-8"));
            
            if(watcher!=null){
            	watcher.close();
            }
            watcher = new FileChangeWatcher(openFile);
            watcher.addIFileChangeListener(this);
            watcher.start();
            
        } catch (IOException ex) {
            Logger.getLogger(MainController.class.getName()).
                    log(Level.SEVERE, null, ex);
        }
        
        
        
    }

    @FXML
    private void onSaveFile(ActionEvent e) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save JFXScad File");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(
                        "JFXScad files (*.jfxscad, *.groovy)",
                        "*.jfxscad", "*.groovy"));
        fileChooser.setInitialDirectory(openFile);
        
        File f = fileChooser.showSaveDialog(null);
        
        if (f == null) {
            return;
        }

        String fName = f.getAbsolutePath();

        if (!fName.toLowerCase().endsWith(".groovy")
                && !fName.toLowerCase().endsWith(".jfxscad")) {
            fName += ".jfxscad";
        }

        try {
            Files.write(Paths.get(fName), getCode().getBytes("UTF-8"));
        } catch (IOException ex) {
            Logger.getLogger(MainController.class.getName()).
                    log(Level.SEVERE, null, ex);
        }
    }

    @FXML
    private void onExportAsSTLFile(ActionEvent e) {

        if (csgObject == null) {
            Action response = Dialogs.create()
                    .title("Error")
                    .message("Cannot export STL. There is no geometry :(")
                    .lightweight()
                    .showError();

            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export STL File");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(
                        "STL files (*.stl)",
                        "*.stl"));

        File f = fileChooser.showSaveDialog(null);

        if (f == null) {
            return;
        }

        String fName = f.getAbsolutePath();

        if (!fName.toLowerCase().endsWith(".stl")) {
            fName += ".stl";
        }

        try {
            eu.mihosoft.vrl.v3d.FileUtil.write(
                    Paths.get(fName), csgObject.toStlString());
        } catch (IOException ex) {
            Logger.getLogger(MainController.class.getName()).
                    log(Level.SEVERE, null, ex);
        }
    }

    @FXML
    private void onExportAsPngFile(ActionEvent e) {

        if (csgObject == null) {
            Action response = Dialogs.create()
                    .title("Error")
                    .message("Cannot export PNG. There is no geometry :(")
                    .lightweight()
                    .showError();

            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export PNG File");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(
                        "Image files (*.png)",
                        "*.png"));

        File f = fileChooser.showSaveDialog(null);

        if (f == null) {
            return;
        }
        jfx3dmanager.saveToPng(f);
    }

    @FXML
    private void onConnect(ActionEvent e) {
    	application.addConnection();
    }

    @FXML
    private void onServoMountSample(ActionEvent e) {

        try {
            String code = IOUtils.toString(this.getClass().
                    getResourceAsStream("ServoMount.jfxscad"),
                    "UTF-8");
            setCode(code);
        } catch (IOException ex) {
            Logger.getLogger(MainController.class.getName()).
                    log(Level.SEVERE, null, ex);
        }

    }

    @FXML
    private void onBatteryHolderSample(ActionEvent e) {

        try {
            String code = IOUtils.toString(this.getClass().
                    getResourceAsStream("BatteryHolder.jfxscad"),
                    "UTF-8");
            setCode(code);
        } catch (IOException ex) {
            Logger.getLogger(MainController.class.getName()).
                    log(Level.SEVERE, null, ex);
        }

    }

    @FXML
    private void onWheelSample(ActionEvent e) {

        try {
            String code = IOUtils.toString(this.getClass().
                    getResourceAsStream("Wheel.jfxscad"),
                    "UTF-8");
            setCode(code);
        } catch (IOException ex) {
            Logger.getLogger(MainController.class.getName()).
                    log(Level.SEVERE, null, ex);
        }

    }

    @FXML
    private void onBreadBoardConnectorSample(ActionEvent e) {

        try {
            String code = IOUtils.toString(this.getClass().
                    getResourceAsStream("BreadBoardConnector.jfxscad"),
                    "UTF-8");
            setCode(code);
        } catch (IOException ex) {
            Logger.getLogger(MainController.class.getName()).
                    log(Level.SEVERE, null, ex);
        }

    }

    @FXML
    private void onBoardMountSample(ActionEvent e) {

        try {
            String code = IOUtils.toString(this.getClass().
                    getResourceAsStream("BoardMount.jfxscad"),
                    "UTF-8");
            setCode(code);
        } catch (IOException ex) {
            Logger.getLogger(MainController.class.getName()).
                    log(Level.SEVERE, null, ex);
        }

    }

    @FXML
    private void onClose(ActionEvent e) {
        System.exit(0);
    }

    @FXML
    private void onAutoCompile(ActionEvent e) {
        autoCompile = !autoCompile;
    }

    public TextArea getLogView() {
        return logView;
    }

	@Override
	public void onFileChange(File fileThatChanged, WatchEvent event) {
		// TODO Auto-generated method stub
		if(fileThatChanged.getAbsolutePath().contains(openFile.getAbsolutePath())){
			System.out.println("Code in "+fileThatChanged.getAbsolutePath()+" changed");
			Platform.runLater(new Runnable() {
	            @Override
	            public void run() {
	            	try {
						setCode(new String(Files.readAllBytes(Paths.get(fileThatChanged.getAbsolutePath())), "UTF-8"));
					} catch (UnsupportedEncodingException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
	            }
	       });

		}else{
			//System.out.println("Othr Code in "+fileThatChanged.getAbsolutePath()+" changed");
		}
	}



	public void disconnect() {
		jfx3dmanager.disconnect();
	}


}