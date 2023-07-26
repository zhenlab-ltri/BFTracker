//optical flow detection. using block method
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

public class OpticalFlow {
	ImageProcessor ip1;
	ImageProcessor ip2;
	int x;
	int y;
	int blocksize;
	int range;
	
	//ip1_ is preframe, ip2_ is current
	OpticalFlow(ImageProcessor ip1_, ImageProcessor ip2_)
	{
		ip1=ip1_;
		ip2=ip2_;
	}
	
	//return vector of best fit direction.
	//xy is the top left of block
	int[] searchfit(int x_, int y_, int blocksize_, int range_)	
	{
		//IJ.log("searchfit "+String.valueOf(x_)+" "+String.valueOf(y_)+" "+String.valueOf(blocksize_)+" "+String.valueOf(range_));
		x=x_;
		y=y_;
		blocksize=blocksize_;
		range=range_;
		//ImageProcessor ip1= imp1.getProcessor();
		ip2.setRoi(x, y, blocksize,blocksize);
		ImageProcessor templateip1=ip2.crop();
		//ImagePlus tempimp=new ImagePlus("",templateip1);
		//tempimp.show();
		//ImageProcessor ip2= imp2.getProcessor();
		//just for 16 bit image
		short[] temppixels=(short[])templateip1.getPixels();
		int scannum=(range*2+1)*(range*2+1);
		int shiftx=0;
		int shifty=0;
		double[] diffabssum=new double[scannum];
		int count=0;
		for(int i=0;i<(range*2+1);i++)
		{
			for(int j=0; j<(range*2+1);j++)
			{
				//IJ.log(String.valueOf(x-(range)+j)+ " "+String.valueOf(y-(range)+i)+ " ");
				ip1.setRoi(x-(range)+j, y-(range)+i, blocksize,blocksize);
				if(x-(range)+j>0 && y-(range)+i>0 && 
						x-(range)+j+blocksize<ip1.getWidth() && y-(range)+i+blocksize<ip1.getHeight())
				{
					ImageProcessor targetip2=ip1.crop();
					short[] targetpixels=(short[])targetip2.getPixels();
					
					double sumofdiffabs=0;
					for(int k=0; k<temppixels.length;k++)
					{
						sumofdiffabs=sumofdiffabs+
						Math.abs(targetpixels[k]-temppixels[k]);
					}
					//IJ.log(String.valueOf(sumofdiffabs));
					diffabssum[count]=sumofdiffabs;
				}
				else
				{
					//something big value.
					diffabssum[count]=ip1.getMax()*blocksize*blocksize;
				}
				count++;
			}
		}
		
		double[] minofdiffabssum=MathEx.min(diffabssum);
		//IJ.log("minofdiffabssum "+String.valueOf(minofdiffabssum[0])+" "+
		//		String.valueOf(minofdiffabssum[1]));
		
		int minindex=(int)minofdiffabssum[1];
		return new int[]{minindex%(range*2+1)-range_, minindex/(range*2+1)-range_};
	}
	
	

}
