package io.cresco.library.app;


import java.util.HashMap;
import java.util.Map;

public class gEdge {

	  public String edge_id;
	  public String node_from;
	  public String node_to;
	  public Map<String, String> params;
	  public String node_from_regiont;
	  public String node_from_agent;
	  public String node_to_region;
	  public String node_to_agent;



	public gEdge(String edge_id, String node_from, String node_to)
	  {
		 this.edge_id = edge_id;
		 this.node_from = node_from;
		 this.node_to = node_to;
		 params = new HashMap<>();
	  }

	public gEdge(String edge_id, String node_from, String node_to, Map<String, String> params)
	{
		this.edge_id = edge_id;
		this.node_from = node_from;
		this.node_to = node_to;
		this.params = params;
	}


	  
	}