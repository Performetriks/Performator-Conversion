package com.performetriks.performator.conversion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map.Entry;
import java.util.TreeMap;

import com.google.common.base.Strings;
import com.google.gson.JsonElement;
import com.performetriks.performator.base.PFR;
import com.performetriks.performator.http.PFRHttp;

/*****************************************************************************
 * Represents a single request extracted from the HAR.
 *****************************************************************************/
public class RequestEntry {
	
	String method = "GET";
	String name = "";
	
	String url = "";
	String urlHost = "";
	String urlQuery = "";
	String urlVariable = "";
	
	int status = 200; // response status
	
	LinkedHashMap<String, String> params = new LinkedHashMap<>();
	String body = "";
	String bodyAsJson = ""; // used for JSON body
	String resourceType = "";
	

	// keep track of all hosts
	public static ArrayList<String> hostList = new ArrayList<>();
	
	//--------------------
	// Headers
	private Integer headersIndex = null;
	private LinkedHashMap<String, String> headers = new LinkedHashMap<>();
	private static int INDEX_COUNTER = 0;
	
	// First is hash, Second is Header ID 
	private static LinkedHashMap<Integer, Integer> headerHashtoIndexMap = new LinkedHashMap<>();

	/*********************************************
	 * Resets counters and such.
	 *********************************************/
	public static void reset() {
		INDEX_COUNTER = 0;
		headerHashtoIndexMap = new LinkedHashMap<>();
	}
	/*********************************************
	 * Returns the name prefixed with a 3 digit
	 * index.
	 *********************************************/
	public String indexedName(int index, boolean sanitizeDots) {
		return PFRConverterUI.threeDigits(index) + "_" + getSanitizedName(sanitizeDots);
	}
		
	/*********************************************
	 * Returns a clone of the host List
	 *********************************************/
	public static ArrayList<String> getHostList() {
		ArrayList<String> clone = new ArrayList<>();
		clone.addAll(hostList);
		return clone;
	}
	
	/*********************************************
	 * Returns a clone of the host List
	 *********************************************/
	public static void clearHostList() {
		 hostList = new ArrayList<>();
	}
	
	/*********************************************
	 * Adds a http parameter.
	 * 
	 * @return instance for chaining
	 *********************************************/
	public RequestEntry param(String name, String value) {
		 params.put(name, value);
		 return this;
	}
	
	/*********************************************
	 * Adds a http header.
	 * @return instance for chaining
	 *********************************************/
	public RequestEntry header(String name, String value) {
		 headers.put(name, value);
		 return this;
	}
	
	/*********************************************
	 * Returns the headers for this entry.
	 * 
	 * @return headers
	 *********************************************/
	public LinkedHashMap<String, String> headers() {
		return headers;
	}
	
	/*********************************************
	 * Returns the headerIndex for this entry.
	 * Should only be called once after all headers
	 * for this entry have been added in whole.
	 * 
	 * @return headers
	 *********************************************/
	protected int headersIndex() {
		
		if(headersIndex == null) {
			StringBuilder concat = new StringBuilder();
			TreeMap<String, String> headersSorted = new TreeMap<>(headers);
			for(Entry<String, String> header : headersSorted.entrySet()) {
				concat.append(header.getKey()).append(header.getValue());
			}
			
			int hash = concat.toString().hashCode();
			if( ! headerHashtoIndexMap.containsKey(hash) ) {
				headerHashtoIndexMap.put(hash, INDEX_COUNTER++);
				
			}
			
			headersIndex = headerHashtoIndexMap.get(hash);
		}
		
		return headersIndex;
	}
	
	/*********************************************
	 * Sets the body.
	 * 
	 * @return instance for chaining
	 *********************************************/
	public RequestEntry body(String body) {
		this.body = body;
		return this;
	}
	
	/*********************************************
	 * Returns the body for this entry.
	 * 
	 * @return String body
	 *********************************************/
	public String body() {
		return body;
	}
	
	/*********************************************
	 * Sets the http method.
	 * 
	 * @return instance for chaining
	 *********************************************/
	public RequestEntry method(String method) {
		this.method = method;
		return this;
	}
	
	/*********************************************
	 * Returns the method for this entry.
	 * 
	 * @return String method
	 *********************************************/
	public String method() {
		return method;
	}
	
	/*********************************************
	 * Sets the http status.
	 * 
	 * @return instance for chaining
	 *********************************************/
	public RequestEntry status(int status) {
		this.status = status;
		return this;
	}
	
	/*********************************************
	 * Returns the status for this entry.
	 * 
	 * @return String status
	 *********************************************/
	public int status() {
		return status;
	}
	
	/*********************************************
	 * Sets the name for this entry.
	 * 
	 * @return instance for chaining
	 *********************************************/
	public RequestEntry name(String name) {
		this.name = name;
		return this;
	}
	
	/*********************************************
	 * Returns the name for this entry.
	 * 
	 * @return String name
	 *********************************************/
	public String name() {
		return name;
	}
	
	/*********************************************
	 * Sets the resourceType for this entry.
	 * 
	 * @return instance for chaining
	 *********************************************/
	public RequestEntry resourceType(String resourceType) {
		this.resourceType = resourceType;
		return this;
	}
	
	/*********************************************
	 * Returns the resourceType for this entry.
	 * 
	 * @return String name
	 *********************************************/
	public String resourceType() {
		return resourceType;
	}
	
	/*********************************************
	 * Returns true if request has a body
	 *********************************************/
	public boolean hasBody() {
		return ( body != null && !body.isBlank());
	}
	
	/*********************************************
	 * Returns true if the request body is of 
	 * type JSON
	 *********************************************/
	public boolean isBodyJSON() {
		
		//--------------------------
		// Check no body
		if(!hasBody()) { return false; }
		
		//--------------------------
		// Check JSON start
		if(! body.startsWith("{")
		&& ! body.startsWith("[")) {
			return false;
		}
		
		//--------------------------
		// Check Null
		try {
			JsonElement e = PFR.JSON.fromJson(body);
			bodyAsJson = PFR.JSON.toJSONPretty(e);
		}catch(Throwable e){
			return false;
		}
		
		return true;
	}
	
	/*********************************************
	 * Set the URL of this Request Entry.
	 *********************************************/
	public void setURL(String URL) {
		
		this.url = URL;
		
		//------------------------
		// Extract Host
		if(URL.contains("://")) {
			this.urlHost = PFR.Text.extractRegexFirst("(.*?//.*?)/", 0, URL);
			this.urlQuery = PFR.Text.extractRegexFirst(".*?//.*?(/.*)", 0, URL);
		}else if(URL.contains("/")) {
			this.urlHost = PFR.Text.extractRegexFirst("(.*?)/", 0, URL);
			this.urlQuery = PFR.Text.extractRegexFirst(".*?(/.*)", 0, URL);
		}else {
			this.urlHost = URL;
		}
		
		//------------------------
		// Define URL Variable
		if( ! hostList.contains(urlHost) && checkIncludeRequest() ) {
			hostList.add(urlHost);
		}
		
		urlVariable = "url_" + hostList.indexOf(urlHost); 

	}
			
	/*****************************************************************************
	 * Check if the requests should be included according to the UI options.
	 *
	 * @return filtered list of HarRequestEntry
	 *****************************************************************************/
	public boolean checkIncludeRequest() {
		
		//------------------------
		// Check Resource Type
		if (PFRConverterUI.cbExcludeCss.isSelected() && "stylesheet".equalsIgnoreCase(resourceType)) return false;
		if (PFRConverterUI.cbExcludeScripts.isSelected() && "script".equalsIgnoreCase(resourceType)) return false;
		if (PFRConverterUI.cbExcludeImages.isSelected() && resourceType.toLowerCase().contains("image")) return false;
		if (PFRConverterUI.cbExcludeFonts.isSelected() && "font".equalsIgnoreCase(resourceType)) return false;

		//------------------------
		// Check the regular expressions
		String regexText = PFRConverterUI.tfExcludeRegex.getText();
		
		for(String regex : regexText.split(",")) {
			if(PFR.Text.getRegexMatcherCached(regex.trim(), url).matches()) {
				return false;
			}
		}
		
		
		return true;

	}
	
	/*****************************************************************************
	 * Create a safe Java identifier from a URL.
	 *
	 * @param url url
	 * @return sanitized name
	 *****************************************************************************/
	public String getSanitizedName(boolean sanitizeDots) {
	
		//-------------------------------
		// Check Null
		if (urlQuery == null) return "request";
		
		//-------------------------------
		// Remove slashes
		
		String sanitized = ( name != null && !name.isBlank() ) 
								? name 
								: ( ! Strings.isNullOrEmpty(urlQuery) ) 
										? urlQuery.substring(1) // Remove slash at the beginning
										: urlHost
								;
		
		if(sanitized.endsWith("/")) {
			sanitized = sanitized.substring(0, sanitized.length()-1); // Remove slash at the end
		}
		
		
		//-------------------------------
		// Remove Special chars
		sanitized = PFRHttp.decode(sanitized);
		int maxLength = (Integer)PFRConverterUI.spMaxNameLength.getValue();
		sanitized = sanitized.replaceAll("[^a-zA-Z0-9\\.]", "_");
		
		if(sanitizeDots) 					{ sanitized = sanitized.replace(".", "_");  }
	
		if (sanitized.length() > maxLength) { sanitized = sanitized.substring(0, maxLength); }
		if (sanitized.isEmpty()) 			{ sanitized = "request"; }
		
		if(sanitized.endsWith("_")) {
			sanitized = sanitized.substring(0, sanitized.length()-1); // Remove _ at the end
		}
		
		//-------------------------------
		// Remove consecutive underscores
		return PFR.Text.replaceAll(sanitized, "__+", "_");
	}
}