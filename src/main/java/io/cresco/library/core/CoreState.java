package io.cresco.library.core;


public interface CoreState {

	public boolean updateController(String jarPath);
	public boolean stopController();
	public boolean restartController();
	public boolean restartFramework();
	public boolean killJVM();

}