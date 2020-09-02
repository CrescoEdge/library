package io.cresco.library.metrics;


import io.micrometer.core.instrument.Meter;

public class CMetric {

	  public String name;
	  public String description;
	  public String group;
	  public String className;
	  public String inodeId;
	  public String resourceId;
	  public String edgeId;
	  public Type type;
	  public MeasureClass measureClass;

	  public CMetric(String name, String description, String group, MeasureClass measureClass)
	  {
	  	  //NODE TYPE
		  this.name = name;
		  this.description = description;
		  this.group = group;
		  this.type = Type.NODE;
		  this.measureClass = measureClass;

	  }

	public CMetric(String name, String description, String group, MeasureClass measureClass, String inodeId, String resourceId)
	{
		//APP TYPE
		this.name = name;
		this.description = description;
		this.group = group;
		this.measureClass = measureClass;
		this.type = Type.APP;
		this.inodeId = inodeId;
		this.resourceId = resourceId;
	}

	public CMetric(String name, String description, String group, MeasureClass measureClass, String edgeId)
	{
		//APP TYPE
		this.name = name;
		this.description = description;
		this.group = group;
		this.measureClass = measureClass;
		this.type = Type.EDGE;
		this.edgeId = edgeId;
	}

	public String getMeterTypeString() {
	  	return getMeterType().toString();
	}

	public Meter.Type getMeterType() {

		Meter.Type meterType = null;
		try {

			switch (this.measureClass) {

				case GAUGE_INT:
					return Meter.Type.GAUGE;
				case GAUGE_LONG:
					return Meter.Type.GAUGE;
				case GAUGE_DOUBLE:
					return Meter.Type.GAUGE;
				case GAUGE_AUTO:
					return Meter.Type.GAUGE;
				case TIMER:
					return Meter.Type.TIMER;
				case DISTRIBUTION_SUMMARY:
					return Meter.Type.DISTRIBUTION_SUMMARY;
				case COUNTER:
					return Meter.Type.COUNTER;
			}

		} catch (Exception ex) {
			ex.printStackTrace();
		}
		return meterType;
	}

	public enum Type {
		APP, //require inode and resource node
	  	NODE, //require nothing
		EDGE; //require edge id

		Type() {

		}
	}

	public enum MeasureClass {
		GAUGE_INT,
		GAUGE_LONG,
		GAUGE_DOUBLE,
		GAUGE_AUTO,
		TIMER,
		DISTRIBUTION_SUMMARY,
		COUNTER;

		MeasureClass() {

		}
	}

	}