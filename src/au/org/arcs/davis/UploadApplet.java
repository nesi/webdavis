package au.org.arcs.davis;

import java.applet.Applet;
import java.awt.Label;

//import javax.swing.JApplet;

public class UploadApplet extends Applet {

	
	public void init() {
		  add(new Label(getParameter("first")));
		  add(new Label(getParameter("second")));
		  add(new Label(getParameter("third")));
		  System.err.println("finished init");
	}
	
	public static void main(String[] args) {

	}
}
