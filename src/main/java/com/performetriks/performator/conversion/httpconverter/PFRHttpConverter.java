package com.performetriks.performator.conversion.httpconverter;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ItemListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.plaf.nimbus.NimbusLookAndFeel;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import javax.swing.text.TabSet;
import javax.swing.text.TabStop;

import org.graalvm.polyglot.Value;

import com.google.common.base.Strings;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.performetriks.performator.base.PFR;
import com.performetriks.performator.conversion.PFRConverterUI;
import com.performetriks.performator.conversion.RequestEntry;
import com.performetriks.performator.conversion.RequestModel;
import com.performetriks.performator.http.PFRHttp;
import com.performetriks.performator.http.PFRHttpRequestBuilder;

/*****************************************************************************
 * A Swing application that loads a HAR (HTTP Archive) and generates Java code that uses
 * com.performetriks.performator.http.PFRHttp to reproduce the HTTP requests.
 *
 * Features:
 * - fullscreen JFrame
 * - left pane (50%) containing all input controls
 * - right pane (50%) containing a textarea with generated Java code (copy/paste)
 * - parses a HAR file using Google Gson into an inner model
 * - all inputs show description tooltips on mouseover (decorator)
 * - every change to any input re-generates the output
 *
 * NOTE: This is a single-file demonstration. The generated Java code in the right pane is
 * textual output and not compiled/executed by this application.
 *****************************************************************************/
public class PFRHttpConverter extends PFRConverterUI {

	private static final long serialVersionUID = 1L;
	
	// ---------------------------
	// UI Elements (left / right)
	// ---------------------------
	private JButton btnChooseHar;
	private JButton btnChoosePostman;
	private JLabel labelFilePath;


	/*****************************************************************************
	 * Main entrypoint: create and show the UI.
	 *
	 * @param args not used
	 *****************************************************************************/
	public static void main(String[] args) {
		SwingUtilities.invokeLater(() -> {
			System.out.println("Starting HTTP Converter ...");
			PFRHttpConverter g = new PFRHttpConverter();
			g.setVisible(true);
		});
	}

	/*****************************************************************************
	 * Constructor: sets up the JFrame and initializes UI components.
	 *****************************************************************************/
	public PFRHttpConverter() {
		super("Performator Http Converter");

	}
	
	/*****************************************************************************
	 * 
	 *****************************************************************************/
	@Override
	public void initializeCustomControls(JPanel controlsPanel) {
		
		if(btnChooseHar == null) {
			btnChooseHar = new JButton("Open HAR...");
			btnChoosePostman = new JButton("Open Postman...");
			labelFilePath = new JLabel("No file chosen");
			
			labelFilePath.setForeground(reallyLight);
			controlsPanel.add(btnChooseHar);
			controlsPanel.add(btnChoosePostman);
			controlsPanel.add(labelFilePath);
			
			
			// Add update listeners
			btnChooseHar.addActionListener(e -> chooseHarFile());
			btnChoosePostman.addActionListener(e -> choosePostmanFile());
		}
	}
	
	/*****************************************************************************
	 * Handles HAR file selection and parsing.
	 *****************************************************************************/
	private void chooseHarFile() {
		JFileChooser chooser = new JFileChooser();
		chooser.setFileFilter(new FileNameExtensionFilter("HTTP Archive (.har)", "har"));
		int res = chooser.showOpenDialog(this);
		if (res == JFileChooser.APPROVE_OPTION) {
			File f = chooser.getSelectedFile();
			labelFilePath.setText(f.getAbsolutePath());
			try {
				parseHarFile(f);
			} catch (Exception ex) {
				JOptionPane.showMessageDialog(
						  this
						, "Failed to parse HAR: " + ex.getMessage()+": \r\n"
								+PFR.Text.stacktraceToString(ex).replace("<br/>", "\r\n")
						, "Error"
						, JOptionPane.ERROR_MESSAGE
					);
				this.setRequestModel(new RequestModel()); // reset model
			}
			regenerateCode();
		}
	}
	
	/*****************************************************************************
	 * Handles HAR file selection and parsing.
	 *****************************************************************************/
	private void choosePostmanFile() {
		JFileChooser chooser = new JFileChooser();
		chooser.setFileFilter(new FileNameExtensionFilter("Postman Collection (.json)", "json"));
		int res = chooser.showOpenDialog(this);
		
		if (res == JFileChooser.APPROVE_OPTION) {
			File f = chooser.getSelectedFile();
			labelFilePath.setText(f.getAbsolutePath());
			try {
				parsePostmanCollection(f);
			} catch (Exception ex) {
				JOptionPane.showMessageDialog(
							this
							, "Failed to parse Postman Collection: "  + ex.getMessage()+": \r\n"
									+PFR.Text.stacktraceToString(ex).replace("<br/>", "\r\n")
							, "Error"
							, JOptionPane.ERROR_MESSAGE
							);
				this.setRequestModel(new RequestModel()); // reset model
			}
			regenerateCode();
		}
	}
	

	/*****************************************************************************
	 * Parse the HAR file into an in-memory HarModel.
	 *
	 * This loads the JSON with GSON and extracts only the fields we need:
	 * - request url
	 * - request method
	 * - request headers
	 * - request postData/text (if present)
	 * - any custom _resourceType from the HAR entry
	 *
	 * @param file HAR file to parse
	 * @throws IOException on IO problems
	 *****************************************************************************/
	private void parseHarFile(File file) throws IOException {
		
		RequestEntry.clearHostList();
		
		try (Reader r = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
			
			//---------------------------------
			// Get Log Element
			JsonElement rootEl = JsonParser.parseReader(r);
			JsonObject root = rootEl.isJsonObject() ? rootEl.getAsJsonObject() : new JsonObject();
			JsonObject log = root.has("log") && root.get("log").isJsonObject() ? root.getAsJsonObject("log") : null;
			
			//---------------------------------
			// Get Entries Array
			List<RequestEntry> entries = new ArrayList<>();
			if (log != null && log.has("entries") && log.get("entries").isJsonArray()) {
				JsonArray arr = log.getAsJsonArray("entries");
				
				//---------------------------------
				// Iterate Entries
				for (JsonElement el : arr) {
					if (!el.isJsonObject()) continue;
					JsonObject entry = el.getAsJsonObject();
					JsonObject req = entry.has("request") && entry.get("request").isJsonObject() ? entry.getAsJsonObject("request") : null;
					JsonObject resp = entry.has("response") && entry.get("response").isJsonObject() ? entry.getAsJsonObject("response") : null;
					
					if (req == null) continue;

					RequestEntry hre = new RequestEntry();
					hre.method( req.has("method") ? req.get("method").getAsString() : "GET");
					hre.setURL( req.has("url") ? req.get("url").getAsString() : "");
					
					//--------------------------------------
					// Get Status
					if ( resp != null 
					  && resp.has("status") 
					  && resp.get("status").isJsonPrimitive()
					  ){
						hre.status(resp.get("status").getAsInt());
					}
					
					//--------------------------------------
					// Get Headers
					if (req.has("headers") && req.get("headers").isJsonArray()) {
						JsonArray hdrs = req.getAsJsonArray("headers");
						for (JsonElement h : hdrs) {
							
							if (!h.isJsonObject()) continue;
							
							JsonObject ho = h.getAsJsonObject();
							String name = ho.has("name") ? ho.get("name").getAsString() : "";
							String value = ho.has("value") ? ho.get("value").getAsString() : "";
							
							if (!name.isEmpty()) hre.header(name, value);
						}
					}
					
					//--------------------------------------
					// Get Post Data
					if (req.has("postData") && req.get("postData").isJsonObject()) {
						JsonObject postdata = req.getAsJsonObject("postData");
						if (postdata.has("text")) {
							hre.body( postdata.get("text").getAsString() );
						}
						// try to extract params if available
						if (postdata.has("params") && postdata.get("params").isJsonArray()) {
							JsonArray params = postdata.getAsJsonArray("params");

							for (JsonElement p : params) {
								if (!p.isJsonObject()) continue;
								JsonObject po = p.getAsJsonObject();
								String name = po.has("name") ? po.get("name").getAsString() : null;
								String value = po.has("value") ? po.get("value").getAsString() : null;
								value = PFRHttp.decode(value);
								if (name != null) hre.param(name, value == null ? "" : value);
							}
						}
					}
					
					//--------------------------------------
					// custom _resourceType: sometimes present in _resourceType or in comment/other
					String resourceType = "";
					if (entry.has("_resourceType")) {
						resourceType = entry.get("_resourceType").getAsString();
					} else if (entry.has("cache") && entry.get("cache").isJsonObject()) {
						// nothing
					} else if (req.has("_resourceType")) {
						resourceType = req.get("_resourceType").getAsString();
					} else if (entry.has("comment")) {
						resourceType = entry.get("comment").getAsString();
					}
					hre.resourceType(resourceType);
					
					entries.add(hre);

				}
			}
			
			this.setRequestModel(new RequestModel(entries));
		}
	}
	
	/*****************************************************************************
	 * Parse a Postman Collection JSON file into an in-memory RequestModel.
	 *
	 * This loads JSON with GSON and extracts:
	 * - request name
	 * - request url
	 * - request method
	 * - headers
	 * - body (raw if available)
	 * - URL query parameters
	 *
	 * Postman collections are recursive: any object may contain an "item" array.
	 *
	 * @param file Postman Collection JSON to parse
	 * @throws IOException on IO problems
	 *****************************************************************************/
	private void parsePostmanCollection(File file) throws IOException {

	    RequestEntry.clearHostList();

	    try (Reader r = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
	        JsonElement rootEl = JsonParser.parseReader(r);
	        JsonObject root = rootEl.isJsonObject() ? rootEl.getAsJsonObject() : new JsonObject();

	        List<RequestEntry> entries = new ArrayList<>();

	        // top-level "item" array
	        if (root.has("item") && root.get("item").isJsonArray()) {
	            JsonArray arr = root.getAsJsonArray("item");
	            parseItemArray(arr, entries, "");
	        }

	        this.setRequestModel(new RequestModel(entries));
	    }
	}
	
	/*****************************************************************************
	 * Recursively process any "item" array in a Postman collection.
	 *****************************************************************************/
	private void parseItemArray(JsonArray items, List<RequestEntry> entries, String path) {

	    for (JsonElement el : items) {
	        if (!el.isJsonObject()) continue;
	        JsonObject itemObj = el.getAsJsonObject();

	        // If this object contains a request, parse it
	        if (itemObj.has("request") && itemObj.get("request").isJsonObject()) {
	            JsonObject req = itemObj.getAsJsonObject("request");

	            RequestEntry hre = new RequestEntry();

	            // -------------------------
	            // Name
	            hre.name( itemObj.has("name") ? itemObj.get("name").getAsString() : "");
	          	
	            if(!path.isBlank()) { 
	          		hre.name( path + PATH_SEPARATOR + hre.name() ); 
	          	}
	            
	            // -------------------------
	            // Method
	            hre.method(req.has("method") ? req.get("method").getAsString() : "GET");

	            // -------------------------
	            // URL
	            String url = extractPostmanURL(req);
	            hre.setURL(url);

	            // -------------------------
	            // Headers
	            
	            if (req.has("header") && req.get("header").isJsonArray()) {
	                JsonArray hdrs = req.getAsJsonArray("header");
	                for (JsonElement h : hdrs) {
	                    
	                	if (!h.isJsonObject()) continue;
	                   
	                    JsonObject ho = h.getAsJsonObject();
	                    
	                    String name = (ho.has("key") && ! ho.get("key").isJsonNull() ) 
	                    				? ho.get("key").getAsString() 
	                    				: "";
	                    
	                    String value = (ho.has("value") && ! ho.get("value").isJsonNull() ) 
	                    				? ho.get("value").getAsString() 
	                    				: "";
	                    
	                    if (!name.isEmpty()) hre.header(name, value);
	                }
	            }

	            // -------------------------
	            // Body
	            if (req.has("body") && req.get("body").isJsonObject()) {
	                JsonObject body = req.getAsJsonObject("body");

	                // raw body
	                if (body.has("raw")) {
	                    hre.body(body.get("raw").getAsString());
	                }
	            }

	            // -------------------------
	            // Query parameters attached to URL
	            extractPostmanParamsToEntry(req, hre);

	            entries.add(hre);
	        }

	        // -------------------------
	        // Recurse deeper if nested "item" exists
	        if (itemObj.has("item") && itemObj.get("item").isJsonArray()) {
	        	
	        	if( itemObj.has("name") ) {
	        		String newPath = (path.isBlank())
	        				? itemObj.get("name").getAsString() 
	        				: path + PATH_SEPARATOR + itemObj.get("name").getAsString();
	        		
	        		parseItemArray(itemObj.getAsJsonArray("item"), entries, newPath);
	        	}else {
	        		parseItemArray(itemObj.getAsJsonArray("item"), entries, path);
	        	}
		          	
	            
	        }
	    }
	}
	
	/*****************************************************************************
	 *
	 *****************************************************************************/
	private String extractPostmanURL(JsonObject postmanItem) {

	    if ( !postmanItem.has("url") ) return "";

	    JsonElement uel = postmanItem.get("url");

	    // "url" may be a string OR an object
	    if (uel.isJsonPrimitive()) {
	        return uel.getAsString();
	    }

	    JsonObject urlObj = uel.getAsJsonObject();

	    StringBuilder sb = new StringBuilder();

	    //------------------------------
	    // protocol://
	    if (urlObj.has("protocol")) {
	        sb.append(urlObj.get("protocol").getAsString()).append("://");
	    }
	    
	    //------------------------------
	    // host segments
	    if (urlObj.has("host") && urlObj.get("host").isJsonArray()) {
	        JsonArray hostArr = urlObj.getAsJsonArray("host");
	        
	        for (int i = 0; i < hostArr.size(); i++) {
	            
	        	if (i > 0) { sb.append("."); }
	            
	            sb.append(hostArr.get(i).getAsString());
	        }
	    }
	    
	    //------------------------------
	    // path segments
	    if (urlObj.has("path") && urlObj.get("path").isJsonArray()) {
	        for (JsonElement p : urlObj.getAsJsonArray("path")) {
	            sb.append("/").append(p.getAsString());
	        }
	    }
	    
	    //------------------------------
	    // Return host string
	    return sb.toString();
	}

	/*****************************************************************************
	 *
	 *****************************************************************************/
	private void extractPostmanParamsToEntry(JsonObject postmanItem, RequestEntry req) {

	    //req.params = new LinkedHashMap<>();

	    if (!postmanItem.has("url")) return;

	    JsonElement uel = postmanItem.get("url");

	    if (!uel.isJsonObject()) return;

	    JsonObject urlObj = uel.getAsJsonObject();

	    if (urlObj.has("query") && urlObj.get("query").isJsonArray()) {
	        JsonArray qarr = urlObj.getAsJsonArray("query");
	        for (JsonElement q : qarr) {
	            if (!q.isJsonObject()) continue;

	            JsonObject qo = q.getAsJsonObject();
	            
	            String key = (qo.has("key") && ! qo.get("key").isJsonNull() ) 
       				? qo.get("key").getAsString() 
       				: "";
       
	            String val = (qo.has("value") && ! qo.get("value").isJsonNull() ) 
       				? qo.get("value").getAsString() 
       				: "";

	            if (key != null) req.param(key, val);
	        }
	    }
	}



}

