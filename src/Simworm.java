import java.util.*;
import java.util.prefs.Preferences;
import java.io.*;
import java.awt.*;
import java.awt.event.*;

import ij.*;
import ij.io.*;
import ij.process.*;
import ij.gui.*;
import ij.plugin.*;
import ij.plugin.filter.*;
import ij.plugin.frame.PlugInFrame; 
import ij.plugin.frame.ThresholdAdjuster;
import ij.measure.Calibration;

public class Simworm {
	ImagePlus imp;
	
	double bodylength;
	double[] widtharray;
	double[][] joint;//joint coordinate
	ArrayList<double[][]> candidate;
	
	Simworm(double length, double[] width)
	{
		bodylength=length;
		System.arraycopy(width, 0, widtharray, 0, widtharray.length);
		candidate=new ArrayList<double[][]>();
		
	}
	
	
	
	
}
