package io.cresco.library.app;


import java.util.HashMap;
import java.util.Map;

public class gEdge {

	  public String edge_id;
	  public String node_from;
	  public String node_to;

	  private Map<String, String> params;
	  private Map<String, String> params_to;
	  private Map<String, String> params_from;

	public gEdge(String edge_id, String node_from, String node_to) {
		 this.edge_id = edge_id;
		 this.node_from = node_from;
		 this.node_to = node_to;
		 params = new HashMap<>();
		 params_to = new HashMap<>();
		 params_from = new HashMap<>();
	  }

	public gEdge(String edge_id, String node_from, String node_to, Map<String, String> params) {
		this.edge_id = edge_id;
		this.node_from = node_from;
		this.node_to = node_to;
		this.params = params;
		params_to = new HashMap<>();
		params_from = new HashMap<>();
	}

	public Map<String,String> getParams() {
		if(params == null) {
			params = new HashMap<>();
		}
		return params;
	}

	public Map<String,String> getParamsTo() {
		if(params_to == null) {
			params_to = new HashMap<>();
		}
		return params_to;
	}

	public Map<String,String> getParamsFrom() {
		if(params_from == null) {
			params_from = new HashMap<>();
		}
		return params_from;
	}


	  
}