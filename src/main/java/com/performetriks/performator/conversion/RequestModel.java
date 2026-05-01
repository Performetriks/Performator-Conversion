package com.performetriks.performator.conversion;

import java.util.ArrayList;
import java.util.List;

/*****************************************************************************
 * Represents the parsed HAR model (only the pieces we need).
 *****************************************************************************/
public class RequestModel {
	List<RequestEntry> entries;

	public RequestModel() {
		this.entries = new ArrayList<>();
	}

	public RequestModel(List<RequestEntry> entries) {
		this.entries = entries;
	}
	
	public void add(RequestEntry entry) {
		this.entries.add(entry);
	}
	
	public void clear() {
		this.entries.clear();
	}
	
	
}