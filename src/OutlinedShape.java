import java.util.*;
import java.awt.Color;
import java.io.*;

import ij.*;
import ij.gui.Plot;
import ij.gui.PlotWindow;
import ij.io.*;
import ij.process.*;


// using worm outline points, detect possible head and tail from how sharp protrude
// if animal coiling up or couldn't find one end, just return false
public class OutlinedShape {

	ImagePlus imp;
	//given data
	int[] xarray;
	int[] yarray;
	int npoints;
	
	//valiables for thresholding etc. could be changeable by worm wize etc. in the futre
	int windowwidth=20;//how far points used calc tangent line.
	int skipsize=10;//how far points used to calc diff of tangent line. over 1.
	double thresholdcoeff=0.05;
	double deltaanglethreshold;//upper this value treated as candidate ends.
	
	
	double[] indexarray;//just for plotting
	//storing features
	double[] smanglediff;
	//after sorting by hight/width, useable for head tail mid calculation.
    ArrayList<OverThreshRegion> otrarraylist;
    int detectedends;//number of possible ends detected 0,1,2
    int sharpendindex;
    int truesharpend;
	int[] sharpendxy; // possible tail x, y
	double sharpendmaxanglediff;
    int roundendindex;
    int trueroundend;
	int[] roundendxy; // possible head
	double roundendmaxanglediff;
	int[] midxy;// middle
	Wormmodel wm;
	
	//plot related
	ImagePlus plotimp;
	Plot plot;
	PlotWindow pw;
	
	
	OutlinedShape(int[] xarray_, int[] yarray_, int npoints_, ImagePlus imp_)
	{
		imp=imp_;
		xarray=xarray_;
		yarray=yarray_;
		npoints=npoints_;
    	detectedends=0;
    	sharpendxy = new int[2];
    	roundendxy =new int[2];
    	midxy=new int[2];
    	//plot=new Plot("plot","x","deltaangle",new double[]{0},new double[]{0});
    	//pw=plot.show();
	}
	
	//if two ends could detected return true
	boolean detectEnds()
	{
		int[] vectora = new int[2];//first vector. storing x and y
		int[] vectorb = new int[2];//second vector. storing x and y
		int[][] subvec =new int[npoints][2];//vecb-veca
		double[] anglediff = new double[npoints];
		indexarray=new double[npoints];
		for(int i=0;i<npoints;i++)
		{
			vectora[0]=xarray[(i+windowwidth)%npoints]-xarray[(i)%npoints];
			vectora[1]=yarray[(i+windowwidth)%npoints]-yarray[(i)%npoints];
			vectorb[0]=xarray[(i+skipsize+windowwidth)%npoints]-xarray[(i+skipsize)%npoints];
			vectorb[1]=yarray[(i+skipsize+windowwidth)%npoints]-yarray[(i+skipsize)%npoints];
			subvec[i][0]=vectorb[0]-vectora[0];
			subvec[i][1]=vectorb[1]-vectora[1];
			anglediff[i]=MathEx.getAngleDiff(vectora[0],vectora[1],vectorb[0],vectorb[1]);//Learning vector calc. was good, need one step farther. average vec.
			indexarray[i]=(double)i;
			//IJ.log(String.valueOf(vectora[0])+" "+String.valueOf(vectora[1])+" "+String.valueOf(subvec[i][0])+" "+String.valueOf(subvec[i][1])+" "+String.valueOf(anglediff[i]));
		}
		//IJ.log("pre plot");
		//smanglediff=MathEx.boxCar(anglediff, 20,true);
		//smanglediff=anglediff;//introductiono of skipsize reduced noise. try
		//use runMed
		smanglediff=MathEx.runMed(anglediff,5,true);
        //plot=new Plot("plot","x","deltaangle",new double[]{0},new double[]{0});
        deltaanglethreshold=thresholdcoeff*skipsize;
        //plot.setLimits(0,npoints,-deltaanglethreshold,deltaanglethreshold*4);
		//plot.setColor(Color.black);
        //plot.addPoints(indexarray,smanglediff,2);
        //plot.show();
        
        boolean regionflag=false;//use where the region started and end.

        //this flag turn on if lower than threshold
        boolean scannedflag=false;
        boolean[] isscanned = new boolean[npoints];
        otrarraylist= new ArrayList<OverThreshRegion>();
        OverThreshRegion otr = new OverThreshRegion();
        otrarraylist.add(otr);
        int count=0;
        //IJ.log("before while");
        while(isscanned[count]==false && isscanned[count%npoints]==false)
        {
        	//IJ.log(String.valueOf(count));
        	if(smanglediff[count]<=deltaanglethreshold)
        	{
        		scannedflag=true;
        	}
        	if(scannedflag==true)
        	{
        		isscanned[count]=true;
        	}
        	if(regionflag==true)
        	{
        		OverThreshRegion tempotr=otrarraylist.get(otrarraylist.size()-1);
        		tempotr.putPoint(count,smanglediff[count]);
        	}
        	
        	if(smanglediff[count]<=deltaanglethreshold && smanglediff[(count+1)%npoints]>deltaanglethreshold)
        	{
        		OverThreshRegion tempotr=otrarraylist.get(otrarraylist.size()-1);
        		if(tempotr.index!=null)
        		{
        	        otrarraylist.add(new OverThreshRegion());        			
        		}
        		tempotr=otrarraylist.get(otrarraylist.size()-1);
        		tempotr.setStart((count+1)%npoints);
        		regionflag=true;
        	}
        	else if(scannedflag==true && smanglediff[count]>deltaanglethreshold && smanglediff[(count+1)%npoints]<=deltaanglethreshold)
        	{
        		OverThreshRegion tempotr=otrarraylist.get(otrarraylist.size()-1);
        		tempotr.setEnd((count)%npoints);
        		tempotr.calculate();
        		regionflag=false;
        	}
        	count=(count+1)%npoints;
        }//end while
        
        //IJ.log("after while");
        //IJ.log(String.valueOf(otrarraylist.size()));
        if(otrarraylist.get(0).index!=null)
        {
        	
        	//sort by hightperwidth.might need more detaild method.
        	//for example, consider width.
        	//120405 test if it just used maxhight-> looks better
        	Collections.sort(otrarraylist, new OtrComparator());
        	for(int i=0; i<otrarraylist.size();i++)
        	{
        		OverThreshRegion tempotr=otrarraylist.get(i);
        		tempotr.calculate();
        		double[] xindexies= new double[tempotr.index.size()];
        		double[] yvalues= new double[tempotr.anglediffarray.size()];
        		for(int j=0;j<tempotr.index.size();j++)
        		{
        			xindexies[j]=(double)tempotr.index.get(j).intValue();
        			yvalues[j]=tempotr.anglediffarray.get(j).doubleValue();
        		}
        		// sharp end. possible tail
        		if(i==0)
        		{
        			sharpendindex=tempotr.maxindex;
        			truesharpend=(sharpendindex+(windowwidth+skipsize)/2)%npoints;
        			sharpendxy[0]=xarray[truesharpend];
        			sharpendxy[1]=yarray[truesharpend];
        			sharpendmaxanglediff=tempotr.maxvalue;
            		//plot.setColor(Color.red);        			
                	detectedends=1;
        		}
        		else if(i==1)//rounder than first end. possible head
        		{
        			roundendindex=tempotr.maxindex;
        			trueroundend=(roundendindex+(windowwidth+skipsize)/2)%npoints;
        			roundendxy[0]=xarray[trueroundend];
        			roundendxy[1]=yarray[trueroundend];
        			roundendmaxanglediff=tempotr.maxvalue;
        			
        			int midindexa=(truesharpend+trueroundend)/2;
        			int midindexb=(midindexa+npoints/2)%npoints;
        			int[] mida=new int[] {xarray[midindexa],yarray[midindexa]};
        			int[] midb=new int[] {xarray[midindexb],yarray[midindexb]};
        			//IJ.log("mida "+String.valueOf(mida[0])+" "+String.valueOf(mida[1]));
        			//IJ.log("midb "+String.valueOf(midb[0])+" "+String.valueOf(midb[1]));
        			midxy= new int[] {(mida[0]+midb[0])/2,(mida[1]+midb[1])/2};
            		//plot.setColor(Color.blue); 			
                	detectedends=2;
        		}
        		
        		//
        		
        		
        		//plot.addPoints(xindexies,yvalues,0);//circle 0, dot 6. line is 2
        		//plot.setColor(Color.red);
        		//plot.addPoints(new double[]{(double)tempotr.maxindex}, new double[] {tempotr.maxvalue},0);
        		
        	}
        	
            //pw.drawPlot(plot);
        	//plot.show();
        	
        	//wm=new Wormmodel(roundendxy, sharpendxy, midxy, this, imp);
        	//wm=new Wormmodel(this, imp);
        	//wm=new Wormmodel(this, imp, 0);
    		//IJ.log("in os "+String.valueOf(sharpendxy[0])+" "+String.valueOf(sharpendxy[1]));
        	
        	return true;
        }
        else
        {
        	return false;
        }
	}
	
	Wormmodel createWormmodel()
	{
		return new Wormmodel(this, imp, 0);
	}

	//if 1st and 2nd of possible ends seems not correct,
	//get others and return the point
	int[] getPointofOtr(int i)
	{
		int[] returnvale=new int[2];
		if(otrarraylist.size()>i)
		{
			OverThreshRegion tempotr=otrarraylist.get(i);
			tempotr.calculate();
			int tempindex=tempotr.maxindex;
			int truetip=(tempindex+(windowwidth+skipsize)/2)%npoints;
			returnvale[0]=xarray[truetip];
			returnvale[1]=yarray[truetip];
			return returnvale;
		}
		else
			return new int[]{-1};
		
	}
	
	//comparator for hightperwidth. larger one goes first
	//test just maxvalue
	class OtrComparator implements Comparator{
		public int compare(Object obj1, Object obj2)
		{
			OverThreshRegion otr1=(OverThreshRegion)obj1;
			OverThreshRegion otr2=(OverThreshRegion)obj2;
			//return Double.compare(otr1.hightperwidth, otr2.hightperwidth);
			//return Double.compare(otr2.hightperwidth, otr1.hightperwidth);
			return Double.compare(otr2.maxvalue, otr1.maxvalue);
		}
	}
	
	//innerclass having a region that over threshold
	class OverThreshRegion
	{
		//these indeies are correspont with index of x,yarray of OutlinedShape class
		int startindex;
		int endindex;
		ArrayList<Integer> index;
		ArrayList<Double> anglediffarray;
		
		int maxindex;
		double maxvalue;
		int width;
		double hightperwidth;
		
		void setStart(int a)
		{
			startindex=a;
			index=new ArrayList<Integer>();
			anglediffarray =new ArrayList<Double>();
		}
		void putPoint(int a, double b)
		{
			index.add(new Integer(a));
			anglediffarray.add(new Double(b));
		}
		void setEnd(int a)
		{
			endindex=a;
		}
		
		void calculate()
		{
			width=index.size();
			//detect maxindex
			maxvalue=0;
			for(int i=0;i<width;i++)
			{
				if(maxvalue<anglediffarray.get(i).doubleValue())
				{
					maxvalue=anglediffarray.get(i).doubleValue();
					maxindex=index.get(i).intValue();
				}
			}
			hightperwidth=maxvalue/width;
		}
	}

}
