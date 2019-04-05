package io.cresco.library.app;


import java.util.HashMap;
import java.util.Map;

public class gEdge {

	  public String edge_id;
	  public String node_from;
	  public String node_to;
	  public Map<String, String> params;
	  public Map<String, String> params_to;
	  public Map<String, String> params_from;

	public gEdge(String edge_id, String node_from, String node_to)
	  {
		 this.edge_id = edge_id;
		 this.node_from = node_from;
		 this.node_to = node_to;
		 params = new HashMap<>();
		 params_to = new HashMap<>();
		 params_from = new HashMap<>();
	  }

	public gEdge(String edge_id, String node_from, String node_to, Map<String, String> params)
	{
		this.edge_id = edge_id;
		this.node_from = node_from;
		this.node_to = node_to;
		this.params = params;
		params_to = new HashMap<>();
		params_from = new HashMap<>();
	}


	  
}