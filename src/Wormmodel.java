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


public class Wormmodel {
	
	ImagePlus imp;
	OutlinedShape os;
	
	double scale;//longitudinal scale. 
	//the model consist of 20 section. so scale 20 is default
	int[][] joint;//joint coordinate
	float[][] jointf;//float joint
	int[][] tempjoint;
	
	double[] angles;//the length is bornnum+1. both ends are tip. no angle
	//int bornnum=20;//19 joint that have angle, centor of middle born is center.	
	int bornnum=32;//better 2^n format?
	//radian absolute value. upper limit how much bend between born.
	//2 times coiling up with 20 born = 36 degree.
	double anglelimit=2*Math.PI/bornnum;
	double[] widtharray;//with at each joint. how set up at beginning?
	double[][] routlinedpoints;//right left of outline points
	double[][] loutlinedpoints;
	double[] maxwidth;//max width and the index
	int[][] ljoint;//larger joint coordinate
	double[] lwidtharray;
	
	double bodylength;//this value hold length of temporal spline length. not sum of joint coordination.
	
	//this constructor may not usefull
	/*
	Wormmodel(double scale_, double width_)//fornow use simple width. 1/20 of scale?
	{
		scale=scale_;
		widtharray=new double[bornnum-1];
		for(int i=0; i<bornnum-1;i++)
		{
			widtharray[i]=width_;
		}
	}*/
	
	//premidive constructor. this works well when worm posture is reletively stright.
	//but it turning head/tail seems fail to fit.
	Wormmodel(int[] headxy, int[] tailxy, int[] midxy, OutlinedShape os_, ImagePlus imp_)
	{
		imp=imp_;
		os=os_;
		
		joint=new int[bornnum+1][2];
		tempjoint=new int[bornnum+1][2];
		//joint position
		joint[0]=new int[] {headxy[0],headxy[1]};
		joint[joint.length-1]=new int[] {tailxy[0],tailxy[1]};
		joint[joint.length/2]=new int[] {midxy[0],midxy[1]};

		double xstep=(double)(midxy[0]-headxy[0])/(bornnum/2);
		double ystep=(double)(midxy[1]-headxy[1])/(bornnum/2);
		for(int i=1; i<joint.length/2;i++)
		{
			joint[i][0]=joint[0][0]+(int)(xstep*i);
			joint[i][1]=joint[0][1]+(int)(ystep*i);
		}
		
		xstep=(double)(tailxy[0]-midxy[0])/(bornnum/2);
		ystep=(double)(tailxy[1]-midxy[1])/(bornnum/2);
		for(int i=joint.length/2+1; i<joint.length;i++)
		{
			joint[i][0]=joint[joint.length/2][0]+(int)(xstep*(i-joint.length/2));
			joint[i][1]=joint[joint.length/2][1]+(int)(ystep*(i-joint.length/2));
		}

		//width
		widtharray=new double[bornnum+1];//head and tail has zero or one with
		widtharray[0]=1;
		widtharray[widtharray.length-1]=0;
		double h_tlength=Math.sqrt(Math.pow(joint[joint.length-1][0]-joint[0][0], 2)
						+Math.pow(joint[joint.length-1][1]-joint[0][1],2));
		for(int i=1; i<widtharray.length-1;i++)
		{
			widtharray[i]=h_tlength/15;//for now calculate width by 1/15 of length
		}
		//head tail taper. ad hoc for now
		widtharray[1]=widtharray[1]*0.7;
		widtharray[2]=widtharray[2]*0.8;
		widtharray[3]=widtharray[3]*0.9;
		widtharray[widtharray.length-2]=widtharray[widtharray.length-2]*0.4;
		widtharray[widtharray.length-3]=widtharray[widtharray.length-3]*0.6;
		widtharray[widtharray.length-4]=widtharray[widtharray.length-4]*0.8;
		widtharray[widtharray.length-5]=widtharray[widtharray.length-5]*0.9;
		
		showShape();		
        //calcPixvalArea(joint);
        //rough fitting
        //searchJointpos(5,4);        
        //scan whole body and fit the worst segment
        recursiveFit(3,8);        
        //int[][] tmeparray=changeJointandblength(joint, 15, new int[]{200,100},0);
        showShape();
	}
	
	//second constructor. use outlinedShape information, and fit
	Wormmodel(OutlinedShape os_, ImagePlus imp_)
	{
		imp=imp_;
		os=os_;
		joint=new int[bornnum+1][2];
		tempjoint=new int[bornnum+1][2];
		
		//joint position
		joint[0]=new int[] {os.roundendxy[0],os.roundendxy[1]};
		joint[joint.length-1]=new int[] {os.sharpendxy[0],os.sharpendxy[1]};
		
		int htotlength;
		if(os.trueroundend>os.truesharpend)
			htotlength=os.truesharpend +os.xarray.length-os.trueroundend;
		else
			htotlength=os.truesharpend -os.trueroundend;
		int ttohlength=os.xarray.length-htotlength;
		//IJ.log("h t "+String.valueOf(htotlength)+" t h "+String.valueOf(ttohlength));
		int htotstep=htotlength/(joint.length-1);
		int ttohstep=ttohlength/(joint.length-1);
        Overlay overlay=new Overlay();

		for(int i=1; i<joint.length-1;i++)
		{
			int tempx=(os.xarray[(os.trueroundend+htotstep*i+os.xarray.length)%os.xarray.length]
			          +os.xarray[(os.trueroundend-ttohstep*i+os.xarray.length)%os.xarray.length])/2;
			int tempy=(os.yarray[(os.trueroundend+htotstep*i+os.yarray.length)%os.yarray.length]
			          +os.yarray[(os.trueroundend-ttohstep*i+os.yarray.length)%os.yarray.length])/2;
			joint[i][0]=tempx;
			joint[i][1]=tempy;
			PolygonRoi templine=
				new PolygonRoi(
				new int[]{os.xarray[(os.trueroundend+htotstep*i+os.xarray.length)%os.xarray.length]
				          ,os.xarray[(os.trueroundend-ttohstep*i+os.xarray.length)%os.xarray.length]},
				new int[]{os.yarray[(os.trueroundend+htotstep*i+os.yarray.length)%os.yarray.length]
				          ,os.yarray[(os.trueroundend-ttohstep*i+os.yarray.length)%os.yarray.length]}
				,2,Roi.POLYLINE);
	        overlay.add(templine);
	        overlay.setStrokeColor(Color.yellow);
	        overlay.add(templine);
	        imp.setOverlay(overlay);  			
		}
		
		
		//width
		//double h_tlength=Math.sqrt(Math.pow(joint[joint.length-1][0]-joint[0][0], 2)
		//		+Math.pow(joint[joint.length-1][1]-joint[0][1],2));
		PolygonRoi wormroi=new PolygonRoi(os.xarray, os.yarray, os.yarray.length, Roi.POLYGON);
		imp.setRoi(wormroi);
		ImageStatistics ims=imp.getStatistics(ij.measure.Measurements.AREA);
		double wormarea=ims.area;
		imp.killRoi();
		
		widtharray=new double[bornnum+1];//head and tail has zero or one with
		widtharray[0]=1;
		widtharray[widtharray.length-1]=0;
		for(int i=1; i<widtharray.length-1;i++)
		{
			//widtharray[i]=h_tlength/15;//for now calculate width by 1/15 of length
			widtharray[i]=wormarea/350;
		}
		//head tail taper. ad hoc for now
		widtharray[1]=widtharray[1]*0.7;
		widtharray[2]=widtharray[2]*0.8;
		widtharray[3]=widtharray[3]*0.9;
		widtharray[widtharray.length-2]=widtharray[widtharray.length-2]*0.4;
		widtharray[widtharray.length-3]=widtharray[widtharray.length-3]*0.6;
		widtharray[widtharray.length-4]=widtharray[widtharray.length-4]*0.8;
		widtharray[widtharray.length-5]=widtharray[widtharray.length-5]*0.9;
		showShape();		
        //recursiveFit(3,8);  
		/*
		for(int i=1; i<joint.length-1;i++)
		{
			searchJointpos(i,0);
		}
		*/
        //showShape();
	}
	
	//third constructor. use outlinedShape information, put joint at middle of 
	//line that toward closest point of another sides outline
	Wormmodel(OutlinedShape os_, ImagePlus imp_, int v)
	{
		os=os_;
		imp=imp_;
		joint=new int[bornnum+1][2];
		tempjoint=new int[bornnum+1][2];
		int[] sharpendxy=os.sharpendxy;
		int[] roundendxy=os.roundendxy;
		//IJ.log("1 "+String.valueOf(sharpendxy[0]));
		//before determine joint positions, make centor line
		//for easy coding, align outline position h-t and t-h 
		int htotlength;
		if(os.trueroundend>os.truesharpend)
			htotlength=os.truesharpend +os.xarray.length-os.trueroundend;
		else
			htotlength=os.truesharpend -os.trueroundend;
		if(htotlength==0)//not sure if its work. for now put 1
			htotlength=1;
		int ttohlength=os.xarray.length-htotlength;
		//IJ.log("h t "+String.valueOf(htotlength)+" t h "+String.valueOf(ttohlength));
		int htotstep=htotlength/(joint.length-1);
		int ttohstep=ttohlength/(joint.length-1);
		int[][] htotarray=new int[htotlength][2];
		int[][] ttoharray=new int[ttohlength][2];
        for(int i=0;i<htotlength;i++)
        {
        	htotarray[i][0]=
        		os.xarray[(os.trueroundend+i+os.xarray.length)%os.xarray.length];
        	htotarray[i][1]=
        		os.yarray[(os.trueroundend+i+os.yarray.length)%os.yarray.length];
        }
        for(int i=0;i<ttohlength;i++)
        {
        	ttoharray[i][0]=
        		os.xarray[(os.trueroundend-i+os.xarray.length)%os.xarray.length];
        	ttoharray[i][1]=
        		os.yarray[(os.trueroundend-i+os.yarray.length)%os.yarray.length];
        }
        Overlay overlay=new Overlay();
		PolygonRoi templine;
		
		//look for closest point located another side outline 
		//and make a line toward it.
		//the middle of the line is a center point
		ArrayList<int[]> centors=new ArrayList<int[]>();		
		//int[][] centors=new int[(joint.length-2)*2][2];
		//int ascannedindex=0;//if there are 0, most anterior point tend to pick tip
		//int bscannedindex=0;
		int ascannedindex=ttoharray.length/(joint.length-1);
		int bscannedindex=htotarray.length/(joint.length-1);
        for(int i=1;i<joint.length-1;i++)
        {
        	/*
			int ax=os.xarray[(os.trueroundend+htotstep*i+os.xarray.length)%os.xarray.length];
			int ay=os.yarray[(os.trueroundend+htotstep*i+os.yarray.length)%os.yarray.length];
        	int bx=os.xarray[(os.trueroundend-ttohstep*i+os.xarray.length)%os.xarray.length];
			int by=os.yarray[(os.trueroundend-ttohstep*i+os.yarray.length)%os.yarray.length];
			*/
			double amindist=0;
			double bmindist=0;
			double[] aresult=
				serchclosestoutline(htotarray[htotstep*i], 
						ascannedindex, htotarray, ttoharray);
			ascannedindex=(int)aresult[3];
			//IJ.log("ascanned "+String.valueOf(ascannedindex));
			/*
			double[] bresult;
			if(i==6)//debug checking
			{
				bresult=
					serchclosestoutline(ttoharray[ttohstep*i], 
							bscannedindex, ttoharray, htotarray, true);				
			}
			else
			{
				bresult=
				serchclosestoutline(ttoharray[ttohstep*i], 
						bscannedindex, ttoharray, htotarray);
			}*/
			double[] bresult=
				serchclosestoutline(ttoharray[ttohstep*i], 
						bscannedindex, ttoharray, htotarray);
			bscannedindex=(int)bresult[3];
			//IJ.log("bscanned "+String.valueOf(bscannedindex));
			
			int[] acenterpoint=
				new int[]{(int)(htotarray[htotstep*i][0]+aresult[0])/2,
					(int)(htotarray[htotstep*i][1]+aresult[1])/2};
			int[] bcenterpoint=
				new int[]{(int)(ttoharray[ttohstep*i][0]+bresult[0])/2,
					(int)(ttoharray[ttohstep*i][1]+bresult[1])/2};
			//centors[i]=acenterpoint;
			//centors[i+1]=bcenterpoint;
			centors.add(acenterpoint);
			centors.add(bcenterpoint);
			
			//templine=new PolygonRoi(new int[]{htotarray[htotstep*i][0],(int)aresult[0]},
			//		new int[]{htotarray[htotstep*i][1],(int)aresult[1]},2,Roi.POLYLINE);
			//templine.setStrokeColor(Color.green);
	        //overlay.add(templine);
			//templine=new PolygonRoi(new int[]{ttoharray[ttohstep*i][0],(int)bresult[0]},
			//		new int[]{ttoharray[ttohstep*i][1],(int)bresult[1]},2,Roi.POLYLINE);
	        //templine.setStrokeColor(Color.blue);
	        //overlay.add(templine);
	    }
        
        
        //if(true){imp.setOverlay(overlay);return;}
        
        //sort the center point by distance from head or previous point
		ArrayList<int[]> sortedcentors=new ArrayList<int[]>();
		//ArrayList<Integer> centerx=new ArrayList<Integer>();
		//ArrayList<Integer> centery=new ArrayList<Integer>();
		//int[][] sortedcentors=new int[(joint.length-2)*2][2];
        //int[] centerx=new int[sortedcentors.length];
        //int[] centery=new int[sortedcentors.length];
		int[] previouspoint=new int[]{htotarray[0][0],htotarray[0][1]};
		for(int i=0; i<(joint.length-2)*2;i++)
		{
			int[][] arraycentors=(int[][])centors.toArray(new int[0][0]);

			double[] closestindexanddistance=
				MathEx.closestIndexandDistance(previouspoint,arraycentors);
			centors.remove((int)closestindexanddistance[0]);
			//IJ.log("distance "+String.valueOf(closestindexanddistance[1]));
			//if next point is too close, it may make large angle change. so eliminate it.
			if(closestindexanddistance[1]>(htotlength+ttohlength)/100)
			{
				int tempx=arraycentors[(int)closestindexanddistance[0]][0];
				int tempy=arraycentors[(int)closestindexanddistance[0]][1];
				//centerx.add(arraycentors[(int)closestindexanddistance[0]][0]);
				//centery.add(arraycentors[(int)closestindexanddistance[0]][1]);
				sortedcentors.add(new int[] {tempx,tempy});
				previouspoint=new int[]{tempx,tempy};
			}
		}
		//IJ.log("sortedcentors size "+String.valueOf(sortedcentors.size()));
		
        int[] centerx=new int[sortedcentors.size()+2];
        int[] centery=new int[sortedcentors.size()+2];
        centerx[0]=htotarray[0][0];
        centerx[centerx.length-1]=htotarray[htotarray.length-1][0];
        centery[0]=htotarray[0][1];
        centery[centery.length-1]=htotarray[htotarray.length-1][1];
        //centerx[0]=roundendxy[0];
        //centerx[centerx.length-1]=sharpendxy[0];
        //centery[0]=roundendxy[1];
        //centery[centery.length-1]=sharpendxy[1];
        int count=1;
		Iterator itr= sortedcentors.iterator();
		while(itr.hasNext())
		{
			int[] tempxy=(int[])itr.next();
			centerx[count]=tempxy[0];
			centery[count]=tempxy[1];
			count++;
		}
		//the templine is center line 
		templine=
			new PolygonRoi(centerx,centery,centerx.length,Roi.POLYLINE);
        //overlay.add(templine);
		//templine.setStrokeColor(Color.yellow);
        //overlay.add(templine);
        
        //to make evenly segmented center line, measure length
        //and put xy coordination
        /*
		Calibration cal = imp.getCalibration();
        double w = cal.pixelWidth;
        double h = cal.pixelHeight;
        IJ.log("w h "+String.valueOf(w)+" "+String.valueOf(h));
        IJ.log("isSplineFit() "+templine.isSplineFit());
		IJ.log("templine length "+String.valueOf(templine.getLength()));
		IJ.log("templine uncalblength "+String.valueOf(templine.getUncalibratedLength()));
		*/
		templine.fitSplineForStraightening();
		bodylength=templine.getLength();
		//templine.fitSpline();
		/*
		IJ.log("templine length "+String.valueOf(templine.getLength()));
		IJ.log("templine uncalblength "+String.valueOf(templine.getUncalibratedLength()));
		*/
		FloatPolygon fp=templine.getFloatPolygon();
		//Roi castedroi=(Roi)templine;
		//FloatPolygon fp=castedroi.getInterpolatedPolygon();
		//IJ.log("fp length "+String.valueOf(fp.npoints));
		
        float[] evenx=new float[bornnum+1];
        float[] eveny=new float[bornnum+1];
		jointf=new float[bornnum+1][2];
        float step=(float)fp.npoints/bornnum;
        //evenx[0]=htotarray[0][0];
        evenx[evenx.length-1]=fp.xpoints[fp.npoints-1];
        //eveny[0]=htotarray[0][1];
        eveny[eveny.length-1]=fp.ypoints[fp.npoints-1];
        jointf[jointf.length-1]=new float[]{fp.xpoints[fp.npoints-1], 
        		fp.ypoints[fp.npoints-1]};
        joint[joint.length-1]=new int[]{(int)fp.xpoints[fp.npoints-1], 
        		(int)fp.ypoints[fp.npoints-1]};
        //joint[joint.length-1]=new int[]{centerx[centerx.length-1], 
        //		centery[centery.length-1]};
		for(int i=0; i<bornnum;i++)
		{
			evenx[i]=fp.xpoints[(int)(step*i)];
			eveny[i]=fp.ypoints[(int)(step*i)];
			jointf[i]=new float[]{evenx[i], eveny[i]};
			joint[i]=new int[]{(int)evenx[i], (int)eveny[i]};
		}
		//IJ.log("2 "+String.valueOf(joint[joint.length-1][0]));

		PolygonRoi evenline=
			new PolygonRoi(evenx,eveny,bornnum+1,Roi.POLYLINE);
		//evenline.fitSpline();//why this cause error in PolygonRoi getLength();
		//evenline.fitSplineForStraightening();
		evenline.setStrokeColor(Color.blue);
        overlay.add(evenline);
		
        //imp.setOverlay(overlay);  	
        
        
        
		//width setting part
		widtharray=new double[bornnum+1];//head and tail has zero or one with
		widtharray[0]=1;
		//widtharray[widtharray.length-1]=0;
		widtharray[widtharray.length-1]=1;//need width later
		int scannedindex2nd=ttoharray.length/(joint.length-1);
		int htotstep2nd=htotlength/(jointf.length-1);
		for(int i=1; i<widtharray.length-1;i++)
		{
			//widtharray[i]=h_tlength/15;//for now calculate width by 1/15 of length
			//widtharray[i]=wormarea/350;
			double[] tempresult=
				serchclosestoutline(joint[i], scannedindex2nd, new int[][] {{0}},ttoharray);
			scannedindex2nd=(int)tempresult[3];
			
			widtharray[i]=tempresult[2];
		}
		
		showShape();	
		//for saving RAM, remove os, imp
		//angles=calcAngles();
		os=null;
		imp_=null;
		/*
		IJ.log("angles");
		//angles=calcAngles();
		
		

		//fit sine curve to the joint angles. somehow...
		//f(x)=a*sin(b*x+c) (+d was omitted for now)
		//scan each coefficient following range by 1/10 step at first
		// 0<a<(ymax-ymin)*2
		// 0<b<6/x.length*PI ; upto 3 cycle=3x2PI
		// 0<c<2PI/b=1/3*x.length
		double[] forfittingangles=new double[angles.length-2];
		for(int i=0;i<angles.length-2;i++)
		{
			forfittingangles[i]=angles[i+1];
		}
		double[] yrange=MathEx.range(angles);
		double arangemax=(yrange[1]-yrange[0])*2;
		double brangemax=(double)6/angles.length*Math.PI;
		double[][] fittedresult=fitSine(forfittingangles, 
				new double[]{0,arangemax},
				new double[]{0,brangemax},
				new double[]{0,Math.PI*2},
				10);
		// one more time ?
		IJ.log("fit again");
		
		fittedresult=fitSine(forfittingangles, 
				new double[]{fittedresult[1][0]-arangemax/10,fittedresult[1][0]+arangemax/10},
				new double[]{fittedresult[2][0]-brangemax/10,fittedresult[2][0]+brangemax/10},
				new double[]{fittedresult[3][0]-Math.PI*2/10,
				fittedresult[3][0]+Math.PI*2/10},
				10);
				
		IJ.log("fitted val");
		for(int i=0; i<fittedresult[0].length;i++)
		{
			IJ.log(String.valueOf(fittedresult[0][i]));
		}
		IJ.log(String.valueOf(fittedresult[1][0])+" "+String.valueOf(fittedresult[2][0])+" "
				+String.valueOf(fittedresult[3][0]));
		
		*/
		
		//shiftPhase();
		/*
		for(int i=0; i<5;i++)
		{
			IJ.log(String.valueOf(i));
			propelModel(fittedresult,1);
			System.arraycopy(tempjoint,0,joint,0,joint.length);
			showShape();	
			try{Thread.sleep(1000);}catch(InterruptedException e){}
		}
		*/
				
	}	
	
	
	//use this constructor when use known model. 
	//eg. comparing next timepoint, and after processing.
	Wormmodel(Wormmodel knownmodel, ImagePlus imp_)
	{
		imp=imp_;
		bodylength=knownmodel.bodylength;
		joint=new int[knownmodel.joint.length][2];
		tempjoint=new int[knownmodel.joint.length][2];
		jointf=new float[knownmodel.joint.length][2];
		angles=new double[knownmodel.angles.length];
		bornnum=knownmodel.bornnum;
		anglelimit=knownmodel.anglelimit;
		widtharray=new double[knownmodel.widtharray.length];
		System.arraycopy(knownmodel.widtharray, 0, widtharray, 0, widtharray.length);
		maxwidth=MathEx.max(widtharray);
		//System.arraycopy(knownmodel.joint, 0, joint, 0, joint.length);//this is still sharrow copy
		deepcopyJoint(knownmodel.joint, 0, joint,0, knownmodel.joint.length);
		deepcopyJoint(joint, tempjoint);
		
		//make bit larger shape than model (lwm) and measure lwm-wm 
		setLwm(joint);
		//showShape(ljoint,lwidtharray);
		//calcPixvalArea()
		
		//calcAngles();
		//roughfitModel();
		//recursiveFit(3,8);
		fitModel();
		/*
		fitAnarea(0);
		deepcopyJoint(tempjoint,joint);
		
		fitAnarea(1);
		deepcopyJoint(tempjoint,joint);
		showShape(ljoint,lwidtharray);
		*/
		showShape();
	}
	
	//make modle with joint points and width array
	//used speculated wormmodel, threshold = fieldvaluearray[2];
	//Wormmodel(int[][] joint_, double[] widtharray_, double bodylength_, ImagePlus imp_, int threshold_)
	Wormmodel(ArrayList<int[]> speculatedpoints, double[] widtharray_, 
			double bodylength_, ImagePlus imp_, int threshold_,
			boolean[] headtaildetaction, OutlinedShape os_)
	{
		
		joint=new int[bornnum+1][2];
		widtharray=new double[joint.length];
		double medianbodylength=bodylength_;
		bodylength=bodylength_;
		imp=imp_;
		os=os_;
		int threshold=threshold_;
		//deepcopyJoint(joint_,joint);

		System.arraycopy(widtharray_, 0, widtharray, 0, widtharray_.length); 
		
        	
		//IJ.log("0 "+String.valueOf(joint[joint.length-1][0]));
		//IJ.log(" "+String.valueOf(speculatedpoints.get(0)[0])+" "+
		//		String.valueOf(speculatedpoints.get(0)[1]));
		joint = divideEvenly(speculatedpoints, 32);
		//IJ.log(" "+String.valueOf(joint[0][0])+" "+
		//		String.valueOf(joint[0][1]));
		//IJ.log("2nd "+String.valueOf(joint[1][0])+" "+
		//		String.valueOf(joint[1][1]));
		//joint = divideEvenly(joint, 32);
		showShape();
		pullbyOs(os);
		showShape();
		//try{Thread.sleep(1000);}catch(InterruptedException e){}
		
		//IJ.log("1 "+String.valueOf(joint[joint.length-1][0]));

		//need imp for showing shape
		//showShape();
		int[][] tempjoint=new int[joint.length][2];
		deepcopyJoint(joint,tempjoint);
		//eclipse or java bug? this fittoBounds results affected by
		// divideEvenly(filteredpoints, 32); 50 row below. 
		//the headposition elongated than non divideEvenly case. wiered.
		//if(imp.getSlice()==128)
			//joint=fittoBounds(tempjoint,threshold,headtaildetaction,true);
		//else
			joint=fittoBounds(tempjoint,threshold,headtaildetaction);
		//deepcopyJoint(tempjoint,joint);		
		//IJ.log(" "+String.valueOf(joint[0][0])+" "+
		//		String.valueOf(joint[0][1]));
		//IJ.log("2nd "+String.valueOf(joint[1][0])+" "+
		//		String.valueOf(joint[1][1]));
		//do twice
		//or another fitting method that view from OutlineShape 
		//fittoBounds(threshold);
		showShape();
		
		//if(imp.getSlice()==128)
			//try{Thread.sleep(1000);}catch(InterruptedException e){}
		
		//IJ.log("2 "+String.valueOf(joint[joint.length-1][0]));
		
		
		//filter the sharp bending and correct
		//make triangles by joint and if the hight is higher than x,
		//(this is neary same whith calculating angle, so use calcAngles
		//eliminate the vertex and draw a spline

		filterOutSharpbend();
		joint=fittoBounds(joint,threshold,headtaildetaction);
		showShape();
		//try{Thread.sleep(1000);}catch(InterruptedException e){}
		
		
		//pull joint using outline shape
		//scan each point of outline and finde colosest lrwidth point.
		//put diffvector 
		//average the vector
		//move joint
		pullbyOs(os);
		showShape();
		//try{Thread.sleep(1000);}catch(InterruptedException e){}
		
		
		
		//check the length of body and if it over 5%(?) 
		// and if one of head or tail not detected, shrink it.
		
		double sumofbornlength=this.getJoints().getLength();
		if(Math.abs(sumofbornlength-medianbodylength)>medianbodylength*0.05)
		{
			joint = divideEvenly(
						joint, 32, medianbodylength, headtaildetaction);
			filterOutSharpbend();		
			showShape();
			//try{Thread.sleep(1000);}catch(InterruptedException e){}
			
			joint=fittoBounds(joint,threshold,headtaildetaction);
			//showShape();
			//try{Thread.sleep(1000);}catch(InterruptedException e){}
		}
		showShape();
		//try{Thread.sleep(1000);}catch(InterruptedException e){}
		
		
		imp=null;
		os=null;
	}
	
	void pullbyOs(OutlinedShape os_)
	{
		calcOutlinpos(joint, widtharray);
		int[] closedspointsnum=new int[joint.length];
		double[][] shiftvector=new double[joint.length][2];
		for(int i=0; i<os_.xarray.length;i++)
		{
			double[] cidr=MathEx.closestIndexandDistance(
					new double[]{os_.xarray[i],os_.yarray[i]}, routlinedpoints);
			double[] cidl=MathEx.closestIndexandDistance(
					new double[]{os_.xarray[i],os_.yarray[i]}, loutlinedpoints);
			double[] targetpoint=new double[2];
			int indexofjoint=0;
			if(cidr[1]<cidl[1])
			{
				indexofjoint=(int)cidr[0];
				targetpoint=new double[]{routlinedpoints[indexofjoint][0],
						routlinedpoints[indexofjoint][1]};
			}
			else
			{
				indexofjoint=(int)cidl[0];
				targetpoint=new double[]{loutlinedpoints[indexofjoint][0],
						loutlinedpoints[indexofjoint][1]};				
			}
			
			double[] diffvec=new double[]{(double)(os_.xarray[i]-targetpoint[0]),
					(double)(os_.yarray[i]-targetpoint[1])};
			shiftvector[indexofjoint][0]=shiftvector[indexofjoint][0]+diffvec[0];
			shiftvector[indexofjoint][1]=shiftvector[indexofjoint][1]+diffvec[0];
			closedspointsnum[indexofjoint]++;
		}
		
		for(int i=0; i<shiftvector.length;i++)
		{
			shiftvector[i][0]=shiftvector[i][0]/closedspointsnum[i];
			shiftvector[i][1]=shiftvector[i][1]/closedspointsnum[i];
			joint[i][0]=joint[i][0]+(int)shiftvector[i][0];
			joint[i][1]=joint[i][1]+(int)shiftvector[i][1];
		}
	}

	void filterOutSharpbend()
	{
		angles = calcAngles();
		//double vertexthreshold=bodylength_/(joint.length-1)*Math.sin()
		ArrayList<int[]> filteredpoints=new ArrayList<int[]>();
		for(int i=0; i<joint.length; i++)
		{
			//IJ.log(String.valueOf(joint[i][0])+" "+String.valueOf(joint[i][1]));
			if(Math.abs(angles[i])<Math.PI/4)//60degree? 45? 30 seems not good
			{
				filteredpoints.add(joint[i]);
			}
			else
				IJ.log("eliminated " +" "+ String.valueOf(i));

		}
		if(filteredpoints.size()==joint.length)
		{
			//ok
			IJ.log(String.valueOf(filteredpoints.size()));
		}
		else
		{
			IJ.log("divideevenly "+String.valueOf(filteredpoints.size()));
			joint = divideEvenly(filteredpoints, 32);
			//IJ.log(" "+String.valueOf(joint[0][0])+" "+
			//		String.valueOf(joint[0][1]));
			
			//if(imp.getSlice()==128)
			//{
				//showShape();
				//try{Thread.sleep(1000);}catch(InterruptedException e){}
			//}

			//deepcopyJoint(joint,tempjoint);
			//this fittoBounds behave strangely.
			//#128 slice, when without this code, head is far from neck.
			//whith the code shorten. 
			//if(imp.getSlice()==128)
				//joint=fittoBounds(tempjoint,threshold,headtaildetaction,true);
			//else
				//joint=fittoBounds(tempjoint,threshold,headtaildetaction);
			
			//do twice
			//fittoBounds(threshold);
			//one more time
			//joint = divideEvenly(temppositionarray, 32);
			//fittoBounds(threshold,headtaildetaction);
			//do twice
			//fittoBounds(threshold);
			//showShape();
			
		}
		
	}
	
	
	
	int[][] divideEvenly(ArrayList<int[]> pointsarray, int segmentnum)
	{
		int[][] pointsvector=new int[pointsarray.size()][2];
		Iterator<int[]> itr= pointsarray.iterator();
		int count=0;
		while(itr.hasNext())
		{
			int[] apoint=itr.next();
			
			pointsvector[count]=new int[]{apoint[0],apoint[1]};
			count++;
		}
        //IJ.log(String.valueOf(pointsvector[0][0])+" "+String.valueOf(pointsvector[0][1]));
		return divideEvenly(pointsvector, segmentnum);
	}
	
	int[][] divideEvenly(int[][] jointarray_, int segmentnum)
	{
		return divideEvenly(jointarray_, segmentnum, -1, new boolean[]{true, true});
	}
	
	int[][] divideEvenly(int[][] jointarray_, int segmentnum, double bodylength_, 
			boolean[] headtaildetaction_)
	{
		//int[][] divideEvenly(int segmentnum)
		//IJ.log("3 "+String.valueOf(jointarray_[jointarray_.length-1][0]));
		int[][] returnarray=new int[segmentnum+1][2];
		
		int[][] tempcenter=new int[2][jointarray_.length];
		for(int i=0;i<jointarray_.length;i++)
		{
			tempcenter[0][i]=jointarray_[i][0];
			tempcenter[1][i]=jointarray_[i][1];
			//IJ.log(String.valueOf(tempcenter[0][i])+" "+
			//		String.valueOf(tempcenter[1][i]));
		}
		//sort the center point by distance from head or previous point
		//int[][] sortPoints(int[][] joints_, double bodylength_)	
		//this int[][] has unusual data strage from others. 
		//instead of [index][x,y], [x,y][index] so that easy use for polygon construction
		//tempcenter = sortPoints(joint, bodylength);		
		
		//the templine is possible center line 
		PolygonRoi templine=
			new PolygonRoi(tempcenter[0],tempcenter[1],
					tempcenter[0].length,Roi.POLYLINE);
		//boolean arepointsroi=checkIfwithinRoi(tempcenter[0],tempcenter[1]);
		//while(!arepointsroi)
		//{
			//arepointsroi=checkIfwithinRoi(templine);
		//}
		
		templine.fitSplineForStraightening();
		if(bodylength_<0)
			bodylength=templine.getLength();
		else
			bodylength=bodylength_;

		FloatPolygon fp=templine.getFloatPolygon();
		
        float[] evenx=new float[segmentnum+1];
        float[] eveny=new float[segmentnum+1];
		float[][] jointf_=new float[segmentnum+1][2];
		float step=0;
		if(bodylength_!=0)
			step=(float)fp.npoints/segmentnum;
		else
			step=(float)bodylength_/segmentnum;
		IJ.log("step "+String.valueOf(step));
		
		//IJ.log("4 "+String.valueOf(returnarray[returnarray.length-1][0]));
		
        //if both head tail ok or both ambiguous
		if(bodylength_<0||(headtaildetaction_[0]&&headtaildetaction_[1])
				||(!headtaildetaction_[0]&&!headtaildetaction_[1]))
		{
	        evenx[0]=jointarray_[0][0];
	        eveny[0]=jointarray_[0][1];
	        evenx[evenx.length-1]=fp.xpoints[fp.npoints-1];
	        eveny[eveny.length-1]=fp.ypoints[fp.npoints-1];
	        jointf_[0]=new float[]{evenx[0], eveny[0]};
	        jointf_[jointf_.length-1]=new float[]{jointarray_[jointarray_.length-1][0], 
	        		jointarray_[jointarray_.length-1][1]};
	        returnarray[0]=new int[]{(int)evenx[0], (int)eveny[0]};
	        returnarray[returnarray.length-1]=
	        	new int[]{jointarray_[jointarray_.length-1][0], 
	        		jointarray_[jointarray_.length-1][1]};
			for(int i=1; i<returnarray.length-1;i++)
			{
				evenx[i]=fp.xpoints[(int)(step*i)];
				eveny[i]=fp.ypoints[(int)(step*i)];
				jointf_[i]=new float[]{evenx[i], eveny[i]};
				returnarray[i]=new int[]{(int)evenx[i], (int)eveny[i]};
			}			
		}
		else if(headtaildetaction_[0]&&!headtaildetaction_[1])//head only
		{
	        evenx[0]=jointarray_[0][0];
	        eveny[0]=jointarray_[0][1];
	        //evenx[evenx.length-1]=fp.xpoints[fp.npoints-1];
	        //eveny[eveny.length-1]=fp.ypoints[fp.npoints-1];
	        jointf_[0]=new float[]{evenx[0], eveny[0]};
	        //jointf_[jointf_.length-1]=new float[]{jointarray_[jointarray_.length-1][0], 
	        	//	jointarray_[jointarray_.length-1][1]};
	        returnarray[0]=new int[]{(int)evenx[0], (int)eveny[0]};
	        //returnarray[returnarray.length-1]=
	        //	new int[]{jointarray_[jointarray_.length-1][0], 
	        //		jointarray_[jointarray_.length-1][1]};
			for(int i=1; i<returnarray.length;i++)
			{
				if((int)(step*i)<fp.npoints-1)
				{
					evenx[i]=fp.xpoints[(int)(step*i)];
					eveny[i]=fp.ypoints[(int)(step*i)];
				}
				else//need to extend
				{
					float[] vector=new float[] {evenx[i-1]-evenx[i-2],eveny[i-1]-eveny[i-2]};
					//calculating vec lengthand extend step is precise way,
					//just extend same vector. neary same.
					//double vectorlength=Math.sqrt(Math.pow(vector[0],2),
						//	Math.pow(vector[1],2));
					evenx[i]=evenx[i-1]+vector[0];
					eveny[i]=eveny[i-1]+vector[1];
				}
				jointf_[i]=new float[]{evenx[i], eveny[i]};
				returnarray[i]=new int[]{(int)evenx[i], (int)eveny[i]};
			}			
		}
		else if(!headtaildetaction_[0]&&headtaildetaction_[1])//tail only
		{
	        //evenx[0]=jointarray_[0][0];
	        //eveny[0]=jointarray_[0][1];
	        evenx[evenx.length-1]=fp.xpoints[fp.npoints-1];
	        eveny[eveny.length-1]=fp.ypoints[fp.npoints-1];
	        //jointf_[0]=new float[]{evenx[0], eveny[0]};
	        jointf_[jointf_.length-1]=new float[]{jointarray_[jointarray_.length-1][0], 
	        		jointarray_[jointarray_.length-1][1]};
	        //returnarray[0]=new int[]{(int)evenx[0], (int)eveny[0]};
	        returnarray[returnarray.length-1]=
	        	new int[]{jointarray_[jointarray_.length-1][0], 
	        		jointarray_[jointarray_.length-1][1]};
			for(int i=1; i<returnarray.length;i++)
			{
				if((int)(step*i)<fp.npoints-1)
				{
					evenx[returnarray.length-1-i]=fp.xpoints[(int)(fp.npoints-1-step*i)];
					eveny[returnarray.length-1-i]=fp.ypoints[(int)(fp.npoints-1-step*i)];
				}
				else
				{
					float[] vector=
						new float[] {evenx[returnarray.length-i]-evenx[returnarray.length+1-i],
							eveny[returnarray.length-i]-eveny[returnarray.length+1-i]};
					evenx[returnarray.length-1-i]=evenx[returnarray.length-i]+vector[0];
					eveny[returnarray.length-1-i]=eveny[returnarray.length-i]+vector[1];					
				}
				jointf_[returnarray.length-1-i]=new float[]{evenx[i], eveny[i]};
				returnarray[returnarray.length-1-i]=
					new int[]{(int)evenx[returnarray.length-1-i], 
						(int)eveny[returnarray.length-1-i]};
			}			
			
		}
			
        //jointf_[jointf_.length-1]=new float[]{fp.xpoints[fp.npoints-1], 
        //		fp.ypoints[fp.npoints-1]};
        //returnarray[returnarray.length-1]=new int[]{(int)fp.xpoints[fp.npoints-1], 
        //		(int)fp.ypoints[fp.npoints-1]};
        //above eliminate last point (tail)
		
		//IJ.log("5 "+String.valueOf(returnarray[returnarray.length-1][0]));
		return returnarray;
				
	}

	int[][] fittoBounds(int[][] tempjoint, int threshold_, boolean[] headtaildetaction_)
	{
		return fittoBounds(tempjoint, threshold_, headtaildetaction_, false);
	}	
	int[][] fittoBounds(int[][] tempjoint, int threshold_, boolean[] headtaildetaction_, boolean debug)
	{
		int[][] returnvalue=new int[tempjoint.length][2];
		int threshold=threshold_;
		//void calcOutlinpos(int[][] jointarray, double[] widtharray_);
		calcOutlinpos(tempjoint, widtharray);
		//double[][] routlinedpoints;//right left of outline points
		//double[][] loutlinedpoints;
		//for(int i=0;i<joint.length;i++)
		//{
		//	IJ.log("r "+String.valueOf(routlinedpoints[i][0])
		//			+" "+String.valueOf(routlinedpoints[i][1]));
		//	IJ.log("l "+String.valueOf(loutlinedpoints[i][0])
		//			+" "+String.valueOf(loutlinedpoints[i][1]));
		//}
		//if(true)return;
		
		
		ImageProcessor ip=imp.getProcessor();
		

		//for(int i=0; i<joint.length;i++)
		for(int i=1; i<tempjoint.length-1;i++)//head tail are treated sepalately.
		{
			double xdiff=routlinedpoints[i][0]-loutlinedpoints[i][0];
			double ydiff=routlinedpoints[i][1]-loutlinedpoints[i][1];
			double lengthofcrossline=
				Math.sqrt(Math.pow(xdiff, 2)+
						Math.pow(ydiff, 2));
			double[] pixvalues=new double[(int)lengthofcrossline];
			
			int directionAoverpixels=0;
			boolean flagA=false;
			for(int j=0;j<(int)lengthofcrossline;j++)
			{
				int x = (int)(loutlinedpoints[i][0]+(xdiff/lengthofcrossline)*j);
				int y = (int)(loutlinedpoints[i][1]+(ydiff/lengthofcrossline)*j);
				pixvalues[j]=(double)ip.getPixel(x, y);
				//IJ.log("x "+String.valueOf(x)+" y "+String.valueOf(y)
				//		+" val "+String.valueOf(pixvalues[j]));
				if(pixvalues[j]>threshold && j==0)
				{
					flagA=true;
					directionAoverpixels++;
				}
				else if(pixvalues[j]>threshold && flagA)
				{
					directionAoverpixels++;				
				}
				else
					flagA=false;
			}
			
			int directionBoverpixels=0;
			boolean flagB=false;
			for(int j=0;j<(int)lengthofcrossline;j++)
			{
				if(pixvalues[(int)lengthofcrossline-j-1]>threshold && j==0)
				{
					flagB=true;
					directionBoverpixels++;
				}
				else if(pixvalues[(int)lengthofcrossline-j-1]>threshold && flagB)
				{
					directionBoverpixels++;				
				}
				else
					flagB=false;
			}
			//IJ.log("A overpix "+String.valueOf(directionAoverpixels)
			//		+"B "+ String.valueOf(directionBoverpixels));
			
			
			int shiftvalue=0;


			if(directionAoverpixels==0)
			{
				if(directionBoverpixels==0)
				{
					//nothing to do
				}
				else if(directionBoverpixels>0)
				{
					//shiftvalue=-directionBoverpixels/2;
					shiftvalue=-directionBoverpixels+1;
				}
			}
			else if(directionAoverpixels>0)
			{
				if(directionBoverpixels==0)
				{
					//shiftvalue=directionAoverpixels/2;
					shiftvalue=directionAoverpixels-1;
				}
				else if(directionBoverpixels>0)//if both have it, move toward mid
				{
					if(directionAoverpixels == (int)lengthofcrossline)
					{
						//this line not crossed on worm. 
						//need to look for where is the worm
						boolean foundworm=false;
						int count=0;
						while(!foundworm)//while or 20?
						{
							//if outer of this way, A type
							int ax = (int)(routlinedpoints[count][0]
							         +(xdiff/lengthofcrossline)*count);
							int ay = (int)(routlinedpoints[count][1]
							         +(ydiff/lengthofcrossline)*count);
							//this way is B
							int bx = (int)(loutlinedpoints[count][0]
							         -(xdiff/lengthofcrossline)*count);
							int by = (int)(loutlinedpoints[count][1] 
							         -(ydiff/lengthofcrossline)*count);
							if(ip.getPixel(ax, ay)<threshold)
							{
								foundworm=true;
								shiftvalue=(int)lengthofcrossline+count;
							}
							else if(ip.getPixel(bx, by)<threshold)
							{
								foundworm=true;
								shiftvalue=(int)(lengthofcrossline*-1)-count;								
							}							
							count++;
						}						
					}					
				}
				else if(directionAoverpixels-1>directionBoverpixels)
				{
					shiftvalue=(directionAoverpixels-directionBoverpixels)/2;
				}
				else if(directionBoverpixels-1>directionAoverpixels)
				{
					shiftvalue=-(directionBoverpixels-directionAoverpixels)/2;						
				}
				
			}
				
			//IJ.log("shiftvalue "+String.valueOf(shiftvalue));
			
			//IJ.log("old jointpos "+String.valueOf(joint[i][0])+" "+String.valueOf(joint[i][1]));
			//shift joint
			int delx = tempjoint[i][0]+(int)((xdiff/lengthofcrossline)*shiftvalue);
			int dely = tempjoint[i][1]+(int)((ydiff/lengthofcrossline)*shiftvalue);
			
			//IJ.log("new jointpos "+String.valueOf(delx)+" "+String.valueOf(dely));
			tempjoint[i][0]=delx;
			tempjoint[i][1]=dely;
			if(debug)
			{
				showShape(tempjoint,Color.yellow);
				try{Thread.sleep(300);}catch(InterruptedException e){}
			}
		}
		
		//head or tail process
		if(ip.getPixel(tempjoint[0][0], tempjoint[0][1])>threshold 
				&& !headtaildetaction_[0])
		{
			IJ.log("over at "+String.valueOf(tempjoint[0][0])+" "+String.valueOf(tempjoint[0][1])
					+" " +String.valueOf(ip.getPixel(tempjoint[0][0], tempjoint[0][1])));
			double xdiff=tempjoint[1][0]-tempjoint[2][0];
			double ydiff=tempjoint[1][1]-tempjoint[2][1];
			double templength=Math.sqrt(Math.pow(xdiff,2)+Math.pow(ydiff,2));
			int[] temppos=new int[]{tempjoint[1][0]+(int)(xdiff/templength*templength),
					tempjoint[1][1]+(int)(ydiff/templength*templength)};
			IJ.log("temppos "+String.valueOf(temppos[0])+" "+String.valueOf(temppos[1])
					+" " +String.valueOf(ip.getPixel(temppos[0], temppos[1])));
			if(ip.getPixel(temppos[0],temppos[1])>threshold)
			{
				IJ.log("at "+String.valueOf(temppos[0])+" "+String.valueOf(temppos[1])
						+" " +String.valueOf(ip.getPixel(temppos[0], temppos[1])));
				int count=0;
				while(ip.getPixel(temppos[0],temppos[1])>threshold)
				{
					IJ.log(""+String.valueOf(temppos[0])+" "+String.valueOf(temppos[1])
							+" " +String.valueOf(ip.getPixel(temppos[0], temppos[1])));					 
					temppos=new int[]{tempjoint[1][0]+(int)(xdiff/templength*(templength-count)),
							tempjoint[1][1]+(int)(ydiff/templength*(templength-count))};
					count++;
				}
			}
			tempjoint[0][0]=temppos[0];
			tempjoint[0][1]=temppos[1];
		}
		if(ip.getPixel(tempjoint[tempjoint.length-1][0], tempjoint[tempjoint.length-1][1])>threshold
				&& !headtaildetaction_[1])
		{
			double xdiff=tempjoint[tempjoint.length-2][0]-tempjoint[tempjoint.length-3][0];
			double ydiff=tempjoint[tempjoint.length-2][1]-tempjoint[tempjoint.length-3][1];
			double templength=Math.sqrt(Math.pow(xdiff,2)+Math.pow(ydiff,2));
			int[] temppos=new int[]{
					tempjoint[tempjoint.length-2][0]+(int)(xdiff/templength*templength),
					tempjoint[tempjoint.length-2][1]+(int)(ydiff/templength*templength)};
			if(ip.getPixel(temppos[0],temppos[1])>threshold)
			{
				int count=0;
				while(ip.getPixel(temppos[0],temppos[1])>threshold)
				{
					temppos=new int[]{
							tempjoint[tempjoint.length-2][0]+(int)(xdiff/templength*(templength-count)),
							tempjoint[tempjoint.length-2][1]+(int)(ydiff/templength*(templength-count))};
					count++;
				}
			}
			tempjoint[tempjoint.length-1][0]=temppos[0];
			tempjoint[tempjoint.length-1][1]=temppos[1];
		}
		deepcopyJoint(tempjoint, returnvalue);
		return returnvalue;
	}
	
	//0 of joints_ was used at start point and sorted arrya by distance from it
	//retruns int[][] that has unusual data strage from others. 
	//instead of [index][x,y], [x,y][index] so that easy use for polygon construction
	int[][] sortPoints(int[][] joints_, double bodylength_)	
	{
		//sort the center point by distance from head or previous point
		ArrayList<int[]> centors=new ArrayList<int[]>();	
		for(int i=1; i<joints_.length-1;i++)
		{
			centors.add(new int[] {joints_[i][0],joints_[i][1]});
		}
		int pointsnum=centors.size();
		ArrayList<int[]> sortedcentors=new ArrayList<int[]>();
		int[] previouspoint=new int[]{joints_[0][0],joints_[0][1]};
		for(int i=0; i<pointsnum;i++)
		{
			int[][] arraycentors=(int[][])centors.toArray(new int[0][0]);

			double[] closestindexanddistance=
				MathEx.closestIndexandDistance(previouspoint,arraycentors);
			centors.remove((int)closestindexanddistance[0]);
			//IJ.log("distance "+String.valueOf(closestindexanddistance[1]));
			//if next point is too close, it may make large angle change. so eliminate it.
			if(closestindexanddistance[1]>(bodylength_)/100)
			{
				int tempx=arraycentors[(int)closestindexanddistance[0]][0];
				int tempy=arraycentors[(int)closestindexanddistance[0]][1];
				//centerx.add(arraycentors[(int)closestindexanddistance[0]][0]);
				//centery.add(arraycentors[(int)closestindexanddistance[0]][1]);
				sortedcentors.add(new int[] {tempx,tempy});
				previouspoint=new int[]{tempx,tempy};
			}
		}		//make center line and divide
		
		//this int[][] has unusual data strage from others. 
		//instead of [index][x,y], [x,y][index] so that easy use for polygon construction
        int[][] centerarray=new int[2][sortedcentors.size()+2];
        //int[] centery=new int[sortedcentors.size()+2];
        centerarray[0][0]=joints_[0][0];
        centerarray[0][centerarray[0].length-1]=joints_[joints_.length-1][0];
        centerarray[1][0]=joints_[0][1];
        centerarray[1][centerarray[1].length-1]=joints_[joints_.length-1][1];
        int count=1;
		Iterator itr= sortedcentors.iterator();
		while(itr.hasNext())
		{
			int[] tempxy=(int[])itr.next();
			centerarray[0][count]=tempxy[0];
			centerarray[1][count]=tempxy[1];
			count++;
		}	
		return centerarray;
	}
	
	//not implemented yet
	boolean checkIfwithinRoi(int[] xarray, int[] yarray)
	{
		//the templine is center line 
		PolygonRoi templine=
			new PolygonRoi(xarray,yarray,xarray.length, Roi.POLYLINE);
		templine.fitSplineForStraightening();
		bodylength=templine.getLength();

		FloatPolygon fp=templine.getFloatPolygon();
		
        float[] evenx=new float[bornnum+1];
        float[] eveny=new float[bornnum+1];
		jointf=new float[bornnum+1][2];
        float step=(float)fp.npoints/bornnum;
        //evenx[0]=htotarray[0][0];
        evenx[evenx.length-1]=fp.xpoints[fp.npoints-1];
        //eveny[0]=htotarray[0][1];
        eveny[eveny.length-1]=fp.ypoints[fp.npoints-1];
        jointf[jointf.length-1]=new float[]{fp.xpoints[fp.npoints-1], 
        		fp.ypoints[fp.npoints-1]};
        joint[joint.length-1]=new int[]{(int)fp.xpoints[fp.npoints-1], 
        		(int)fp.ypoints[fp.npoints-1]};
		for(int i=0; i<bornnum;i++)
		{
			evenx[i]=fp.xpoints[(int)(step*i)];
			eveny[i]=fp.ypoints[(int)(step*i)];
			jointf[i]=new float[]{evenx[i], eveny[i]};
			joint[i]=new int[]{(int)evenx[i], (int)eveny[i]};
		}	
		
		return true;
	}
	
	//set up larger worm model, which has 2 more joint pos and
	//added half maxwidth to the width.
	void setLwm(int[][] jointarray)
	{
		//ljoint has 2 more points.
		ljoint=new int[jointarray.length+2][2];
		deepcopyJoint(jointarray, 0, ljoint,1, jointarray.length);
		
		//head extention
		int[] vectowardhead= 
			new int[] {jointarray[0][0]-jointarray[1][0],jointarray[0][1]-jointarray[1][1]};
		//half of maxwidth use as width of outer extent region
		double sizeofvectowardhead=
			Math.sqrt(Math.pow(vectowardhead[0],2)+Math.pow(vectowardhead[1],2));
		ljoint[0][0]=
			jointarray[0][0]+(int)((double)vectowardhead[0]/sizeofvectowardhead*maxwidth[0]);
		ljoint[0][1]=
			jointarray[0][1]+(int)((double)vectowardhead[1]/sizeofvectowardhead*maxwidth[0]);
		//IJ.log("ljoint[0] "+String.valueOf(ljoint[0][0])+" "+String.valueOf(ljoint[0][1]));
		//tail extention
		int[] vectowardtail= 
			new int[] {jointarray[jointarray.length-1][0]-jointarray[jointarray.length-2][0],
				jointarray[jointarray.length-1][1]-jointarray[jointarray.length-2][1]};
		//half of maxwidth use as width of outer extent region
		double sizeofvectowardtail=
			Math.sqrt(Math.pow(vectowardtail[0],2)+Math.pow(vectowardtail[1],2));
		ljoint[ljoint.length-1][0]=
			jointarray[jointarray.length-1][0]+
			(int)((double)vectowardtail[0]/sizeofvectowardtail*maxwidth[0]);
		ljoint[ljoint.length-1][1]=
			jointarray[jointarray.length-1][1]+
			(int)((double)vectowardtail[1]/sizeofvectowardtail*maxwidth[0]);
		//IJ.log("ljoint[last] "+String.valueOf(ljoint[0][0])+" "+String.valueOf(ljoint[0][1]));
		
		lwidtharray=new double[widtharray.length+2];
		lwidtharray[0]=1;
		lwidtharray[lwidtharray.length-1]=0;
		//System.arraycopy(widtharray, 0, lwidtharray, 1, widtharray.length);
		for(int i=1; i<lwidtharray.length-1;i++)
		{
			lwidtharray[i]=widtharray[i-1]+maxwidth[0];
			//IJ.log("w "+String.valueOf(widtharray[i-1])
			//		+" lw "+String.valueOf(lwidtharray[i]));
		}
	}

	 
	
	//try fine fitting using large worm model
	void fitModel()
	{
		for(int i=0; i<joint.length;i++)
		{
			//IJ.log("joint n"+String.valueOf(joint[i][0])
			//		+" "+"joint n"+String.valueOf(joint[i][1]));
			fitAnarea(i);
			//IJ.log("tempjoint n"+String.valueOf(tempjoint[i][0])
			//		+" "+"joint n"+String.valueOf(tempjoint[i][1]));
			deepcopyJoint(tempjoint,joint);
			//IJ.log("joint n"+String.valueOf(joint[i][0])
			//		+" "+"joint n"+String.valueOf(joint[i][1]));
		}
	}
		
		
		
	void fitAnarea(int n)
	{
		int[][] tempjoint2=new  int[tempjoint.length][2];
		deepcopyJoint(tempjoint,tempjoint2);
		int shiftx=0;
		int shifty=0;
		int overshoot=10;
		double maxval=0;
		int calcfrom=n-2;
		int calcto=n+1;
		if(n<2)
			calcfrom=0;
		else if(n>tempjoint.length-2)
			calcto=tempjoint.length-1;
		int lcalcfrom=calcfrom+1;
		int lcalcto=calcto+1;
		if(n<2)
			lcalcfrom=0;
		else if(n>tempjoint.length-2)
			lcalcto=ljoint.length-1;
		for(int i=0;i<bodylength/bornnum*2;i++)
		{
			
			deepcopyJoint(tempjoint,tempjoint2);
			double[] lvmparvm = new double[4];

			tempjoint2[n][0]=tempjoint[n][0]-3;
			tempjoint2[n][1]=tempjoint[n][1];
			//showShape(tempjoint2);
			double[] wmval=calcPixvalArea(tempjoint2,widtharray,calcfrom,calcto);
			//IJ.log("wmval "+String.valueOf(wmval[0]));
			setLwm(tempjoint2);
			//showShape(ljoint,lwidtharray);
			double[] lwmval=calcPixvalArea(ljoint,lwidtharray,lcalcfrom,lcalcto);
			//IJ.log("lwmval "+String.valueOf(lwmval[0]));
			lvmparvm[0]=lwmval[0]/wmval[0];//fow now just using pixel value. need area?

			tempjoint2[n][0]=tempjoint[n][0];
			tempjoint2[n][1]=tempjoint[n][1]-3;
			//showShape(tempjoint2);
			wmval=calcPixvalArea(tempjoint2,widtharray,calcfrom,calcto);
			setLwm(tempjoint2);
			//showShape(ljoint,lwidtharray);
			lwmval=calcPixvalArea(ljoint,lwidtharray,lcalcfrom,lcalcto);
			lvmparvm[1]=lwmval[0]/wmval[0];//fow now just using pixel value. need area?

			tempjoint2[n][0]=tempjoint[n][0]+3;
			tempjoint2[n][1]=tempjoint[n][1];
			//showShape(tempjoint2);
			wmval=calcPixvalArea(tempjoint2,widtharray,calcfrom,calcto);
			setLwm(tempjoint2);
			//showShape(ljoint,lwidtharray);
			lwmval=calcPixvalArea(ljoint,lwidtharray,lcalcfrom,lcalcto);
			lvmparvm[2]=lwmval[0]/wmval[0];//fow now just using pixel value. need area?

			tempjoint2[n][0]=tempjoint[n][0];
			tempjoint2[n][1]=tempjoint[n][1]+3;
			//showShape(tempjoint2);
			wmval=calcPixvalArea(tempjoint2,widtharray,calcfrom,calcto);
			setLwm(tempjoint2);
			//showShape(ljoint,lwidtharray);
			lwmval=calcPixvalArea(ljoint,lwidtharray,lcalcfrom,lcalcto);
			lvmparvm[3]=lwmval[0]/wmval[0];//fow now just using pixel value. need area?
			for(int j=0; j<4;j++)
			{
				//IJ.log("lvmparvm "+String.valueOf(lvmparvm[j]));
			}
			double[] maxvalandindex=MathEx.max(lvmparvm);
			//IJ.log("maxvalandindex "+String.valueOf(maxvalandindex[0]));
			int maxindex=(int)maxvalandindex[1];
			IJ.log("maxindex "+String.valueOf(maxindex));
			if(maxval<maxvalandindex[0])
			{
				IJ.log("max<maxvalandindex[0] ");
				maxval=maxvalandindex[0];
				if(maxindex==0)
				{
					shiftx=-1;
					shifty=0;
				}
				else if(maxindex==1)
				{
					shiftx=0;
					shifty=-1;
				}
				else if(maxindex==2)
				{
					shiftx=1;
					shifty=0;					
				}
				else if(maxindex==3)
				{
					shiftx=0;					
					shifty=1;
				}
				//check if the bending is ok
				tempjoint2[n][0]=tempjoint[n][0]+shiftx;
				tempjoint2[n][1]=tempjoint[n][1]+shifty;
				if(!checkAnglelimit(tempjoint2,n))
				{
					return;
				}
				tempjoint[n][0]=tempjoint[n][0]+shiftx;
				tempjoint[n][1]=tempjoint[n][1]+shifty;
			}
			else
			{
				IJ.log("return at "+String.valueOf(i));
				return;
			}
		}
		IJ.log("return after full loop ");
		return;
	}
	
	//using after 2nd or later slice
	//try to fit given model to current imp using sine fitted angles.
	//roughly... need fine fitting later, or do fine fitting from the beginning?
	void roughfitModel()
	{
		double[] forfittingangles=new double[angles.length-2];
		for(int i=0;i<angles.length-2;i++)
		{
			forfittingangles[i]=angles[i+1];
		}
		double[] yrange=MathEx.range(angles);
		double arangemax=(yrange[1]-yrange[0])*2;
		double brangemax=(double)6/angles.length*Math.PI;
		double[][] fittedresult=fitSine(forfittingangles, 
				new double[]{0,arangemax},
				new double[]{0,brangemax},
				new double[]{0,Math.PI*2},
				10);
		
		double[] premodelcenter=MathEx.mean(joint);
		//IJ.log("premodelcentor "+String.valueOf(premodelcenter[0])+" "+String.valueOf(premodelcenter[1]));
		double[] evalvalues=new double[10];//how many test?
		int[][][] tempjointarray=new int[evalvalues.length][joint.length][2];
		for(int i=0;i<evalvalues.length;i++)
		{
			propelModel(fittedresult, (double)i-evalvalues.length/2);
			double[] movedcenter=MathEx.mean(tempjoint);
			//IJ.log("movedcenter "+String.valueOf(movedcenter[0])+" "+String.valueOf(movedcenter[1]));
			
			for(int j=0;j<tempjoint.length;j++)
			{
				tempjoint[j][0]=tempjoint[j][0]+(int)(premodelcenter[0]-movedcenter[0]);
				tempjoint[j][1]=tempjoint[j][1]+(int)(premodelcenter[1]-movedcenter[1]);
			}
			
			//System.arraycopy(tempjoint,0,tempjointarray[i],0,tempjoint.length);
			deepcopyJoint(tempjoint,tempjointarray[i]);
			showShape(tempjoint,Color.yellow);
			double[] valandarea=calcPixvalArea(tempjoint);
			evalvalues[i]=valandarea[0]/valandarea[1];
			//IJ.log("evalvalues[i] "+String.valueOf(evalvalues[i]));
			//try{Thread.sleep(100);}catch(InterruptedException e){}
		}
		double minindex=MathEx.min(evalvalues)[1];
		//IJ.log("minindex "+String.valueOf(minindex));
		//System.arraycopy(tempjointarray[(int)minindex],0,joint,0,joint.length);
		deepcopyJoint(tempjointarray[(int)minindex],joint);
		showShape();	
		
	}
	
	//return target xy, distance, and scand index
	//
	double[] serchclosestoutline(int[] xy, int scannedindex_, int[][] ownpoints, 
			int[][] target)
	{
		return serchclosestoutline(xy, scannedindex_, ownpoints, target, false);
	}

	double[] serchclosestoutline(int[] xy, int scannedindex_, int[][] ownpoints, 
			int[][] target, boolean debag)
	{
		int tempscannedindex=scannedindex_;
		int overshootlimit=target.length/5;
		int overshootcount=0;
		double mindistance=target.length;//something big value
		int minindex=scannedindex_;
		double tempdistance=0;
		int starttoendlength=target.length-scannedindex_;
		for(int i=0;i<starttoendlength;i++)
		{
			tempdistance=Math.sqrt(Math.pow(target[scannedindex_+i][0]-xy[0], 2)+
					Math.pow(target[scannedindex_+i][1]-xy[1],2));
			//IJ.log("tempdistance "+String.valueOf(tempdistance));
			/*
	        Overlay overlay=new Overlay();
	        PolygonRoi templine=new PolygonRoi(
					new int[]{xy[0],target[scannedindex_+i][0]},
					new int[]{xy[1],target[scannedindex_+i][1]},
					2,Roi.POLYLINE);
			templine.setStrokeColor(Color.black);
			overlay.add(templine);
			imp.setOverlay(overlay);
			try{Thread.sleep(10);}catch(InterruptedException e){}	
			*/		
			if(tempdistance<mindistance)
			{
				//if the line xy-temppoint cross the outline of this side
				boolean iscrossedline=false;
				if(ownpoints.length>1)
				{
					for(int j=0;j<ownpoints.length;j++)
					{
						//don't check crose to the xy target
						double xytoownpoint=Math.sqrt(Math.pow(xy[0]-ownpoints[j][0], 2)+
						Math.pow(xy[1]-ownpoints[j][1], 2));
						if(xytoownpoint>5 && isOntheline(ownpoints[j],xy,
								target[scannedindex_+i]))
						{
							iscrossedline=true;
							if(debag)
							{
							IJ.log(String.valueOf(i)+" "+
									String.valueOf(xy[0])+" "+
									String.valueOf(xy[1])+" to "+
									String.valueOf(target[scannedindex_+i][0])+" "+
									String.valueOf(target[scannedindex_+i][1])+" "+
									"crossed "+
									String.valueOf(ownpoints[j][0])+" "+
									String.valueOf(ownpoints[j][1]));
					        Overlay overlay=new Overlay();
					        PolygonRoi templine=new PolygonRoi(
									new int[]{xy[0],target[scannedindex_+i][0]},
									new int[]{xy[1],target[scannedindex_+i][1]},
									2,Roi.POLYLINE);
							templine.setStrokeColor(Color.black);
							overlay.add(templine);
							imp.setOverlay(overlay);
							try{Thread.sleep(1000);}catch(InterruptedException e){}	
							}
						}
					}
					//check croosing on the other side outline
					
					if(!iscrossedline)
					{
						for(int j=0; j<target.length;j++)
						{
							//don't check crose to the temporal target
							if(scannedindex_+i-j>5&&scannedindex_+i-j<-5&&
							isOntheline(target[j],xy,target[scannedindex_+i]))
							{
								//IJ.log("cross on another outline");
						        Overlay overlay=new Overlay();
						        PolygonRoi templine=new PolygonRoi(
										new int[]{xy[0],target[scannedindex_+i][0]},
										new int[]{xy[1],target[scannedindex_+i][1]},
										2,Roi.POLYLINE);
								templine.setStrokeColor(Color.black);
								overlay.add(templine);
								imp.setOverlay(overlay);
								try{Thread.sleep(100);}catch(InterruptedException e){}	

								iscrossedline=true;
							}
						}
					}
					
				}
				if(!iscrossedline)
				{
					mindistance=tempdistance;
					minindex=tempscannedindex;
				}
			}
			else//it over the min distance point
			{
				if(overshootcount==overshootlimit)
				{
					return new double[]{target[minindex][0], 
						target[minindex][1], mindistance, minindex};					
				}
				else
					overshootcount++;
			}
			tempscannedindex++;
		}
		
		
		return new double[]{target[minindex][0], 
				target[minindex][1], mindistance, minindex};
	}
	
	boolean isOntheline(int[] test, int[] a, int[] b)
	{
		//check if test-a is pallele with a-b
		int theouter=MathEx.getOuter2D(a[0]-test[0], a[1]-test[1], b[0]-a[0],b[1]- a[1]);
		//==0 cant detect neary on line. for now use 10?
		//need to be adjustable. window hight/20?
		if(theouter<10 && theouter>-10)
		{
			if(MathEx.getInner2D(a[0]-test[0], a[1]-test[1], 
					b[0]-test[0], b[1]-test[1])<0)
			{
				return true;
			}
			
		}
		return false;
	}
	
	
	//recursively do the searchJointpos 2^n=a. 
	//eg. if there are 16 joint, recursiveFit(3,8) will fill whole body
	//this works well when worm is relatively straight. but if it turning, not good.
	void recursiveFit(int n, int a)
	{
		searchJointpos(a, (int)(Math.pow(2,n)-1));
		IJ.log("a "+String.valueOf(a));
		if(n>0)
		{
			recursiveFit(n-1,(int)(a-Math.pow(2,n-1)));
			recursiveFit(n-1,(int)(a+Math.pow(2,n-1)));
		}
	}
	
	//serchjointpos that has least pixel value. white background.
	void searchJointpos(int n, int range)
	{
        //int n=5;
        //int range=4;
		IJ.log("joint n "+String.valueOf(n));
		int afixedpointindex=n-range-1;
		int pfixedpointindex=n+range+1;
		if(afixedpointindex<0)
			afixedpointindex=0;
		if(pfixedpointindex>joint.length-1)
			pfixedpointindex=joint.length-1;
		int[] fixtofixvector=new int[]{joint[pfixedpointindex][0]-joint[afixedpointindex][0],
				joint[pfixedpointindex][1]-joint[afixedpointindex][1]};
		float[] verticalveca=new float[]{-fixtofixvector[1],fixtofixvector[0]};
		//float[] verticalvecb=new float[]{fixtofixvector[1],-fixtofixvector[0]};
		float verticalvecsize=(float)Math.sqrt(Math.pow(verticalveca[0],2)+Math.pow(verticalveca[1],2));
		float[] verticalvecunit= new float[]{verticalveca[0]/verticalvecsize, verticalveca[1]/verticalvecsize};
		double[] pixvalandarea=calcPixvalArea(joint);
		double minpixvalue=pixvalandarea[0]/pixvalandarea[1];
		float[] nextvec=new float[]{verticalvecunit[0],verticalvecunit[1]};
		double[] pixvalhist=new double[2];
		for(int i=0;i<40;i++)
		{
			//IJ.log("range "+String.valueOf(range));
			double pixvalues;
			//System.arraycopy(joint, 0, tempjoint, 0, joint.length);
			deepcopyJoint(joint, 0, tempjoint, 0, joint.length);
			int[] target=new int[]{(int)(tempjoint[n][0]+nextvec[0]*2),
					(int)(tempjoint[n][1]+nextvec[1]*2)};	
			//check if angel of the joint is over the limit
			double angleatjoint=
				MathEx.getAngleDiff(tempjoint[pfixedpointindex][0]-tempjoint[n][0],
						tempjoint[pfixedpointindex][1]-tempjoint[n][1],
					tempjoint[n][0]-tempjoint[afixedpointindex][0],
					tempjoint[n][1]-tempjoint[afixedpointindex][1]);
			//if the angle of the joint is larger than anglelimit
			if(Math.abs(angleatjoint)>anglelimit)
			{
				IJ.log("angle "+String.valueOf(angleatjoint));
				//target=new int[]{(int)(joint[n][0]-nextvec[0]/verticalvecsize*2),
				//		(int)(joint[n][1]-nextvec[1]/verticalvecsize*2)};
				
				//changeJointandblength(n,target,range);
				//changeJointandangles(tempjoint, n, target, range);
				tempjoint=changeJointandblength(tempjoint, n, target, range);
				//showShape();
				//return;
			}
			else
			{
				tempjoint=changeJointandblength(tempjoint, n,target,range);
			}
			
			double[] temppixvalandarea=calcPixvalArea(tempjoint);
			pixvalues=temppixvalandarea[0]/temppixvalandarea[1];
			pixvalhist[i%2]=pixvalues;
			
			if(i==0)// first, just took data and move opposit side
			{
				float tempx=verticalvecunit[0];
				float tempy=verticalvecunit[1];
				nextvec=new float[]{-tempx,-tempy};
			}
			//else if(i==1)
			else
			{
				//if minpixvalue is smaller than i=0 and 1 trial, dont need change. done
				if(minpixvalue<=pixvalhist[0]&&minpixvalue<=pixvalhist[1])
				{
					return;
					//minpixvalue=pixvalues;
				}
				else if(pixvalhist[(i+1)%2]<pixvalhist[(i)%2])
				{
					if(i==1)//go opposite direction (with 2x length?)
					{
						minpixvalue=pixvalhist[0];
						//nextvec=new float[]{verticalvecunit[0]*2,verticalvecunit[1]*2};
						nextvec=new float[]{verticalvecunit[0]*2,verticalvecunit[1]*2};
					}
					else
					{
						return;
					}
				}
				else//continue to go this way
				{
					//System.arraycopy(tempjoint, 0, joint, 0, joint.length);
					deepcopyJoint(tempjoint, 0, joint, 0, joint.length);
					minpixvalue=pixvalhist[i%2];
				}
			}
			/*
			else if(pixvalhist[0]<pixvalues&&pixvalhist[1]<pixvalues)//if it overshooted, return to previous position
			{
				showShape();
				return;
			}
			else
			{
				System.arraycopy(tempjoint, 0, joint, 0, joint.length);									
			}*/

			
			showShape();
			//try{Thread.sleep(100);}catch(InterruptedException e){}
		}		
	}
	
	void showShape()
	{
		showShape(joint,Color.yellow);
	}
	void showShape(int[][] joint_, double[] widtharray_, Color col)
	{
        PolygonRoi wormjoints = getJoints(joint_);
        wormjoints.setStrokeColor(col);
        Overlay overlay=new Overlay();
        overlay.add(wormjoints);
        for(int j=0;j<joint_.length-1;j++)
        {
        	PolygonRoi asegment = getAnarea(j,joint_,widtharray_);
        	asegment.setStrokeColor(col);
        	overlay.add(asegment);
        }
        imp.setOverlay(overlay);  
	}
	
	void showShape(int[][] joint_, Color col)
	{
		showShape(joint_, widtharray, col);
	}
	//this is used in BFtracer after processing.
	void showShape(ImagePlus imp_, boolean flag)
	{
		imp=imp_;
		if(flag)
			showShape(joint, Color.green);
		else
			showShape(joint, Color.yellow);
			
		Overlay overlay=imp.getOverlay();
		OvalRoi roundendroi= 
			new OvalRoi(joint[0][0]-5,
					joint[0][1]-5, 10,10);
		roundendroi.setStrokeColor(Color.blue);
		overlay.add(roundendroi);
		
		OvalRoi sharpendroi= 
			new OvalRoi(joint[joint.length-1][0]-5,
					joint[joint.length-1][1]-5, 10,10);
		sharpendroi.setStrokeColor(Color.red);
		overlay.add(sharpendroi);
        imp.setOverlay(overlay);  
	}

	
	PolygonRoi getJoints(int[][] joint_)
	{
		int[] xarray=new int[joint_.length];
		int[] yarray=new int[joint_.length];
		for(int i=0;i<joint_.length;i++)
		{
			xarray[i]=joint_[i][0];
			yarray[i]=joint_[i][1];
			//IJ.log("x "+String.valueOf(joint[i][0]));
		}
		PolygonRoi jointsroi = new PolygonRoi(xarray,yarray,joint_.length,Roi.POLYLINE);
		return jointsroi;
	}
	PolygonRoi getJoints()
	{
		return getJoints(joint);
	}
	
	//change joint position and length of borne around it and reform model. 
	//if need to fix bornlength use changeJointfixedblength,
	//if need to change angles of neighboring joint use changeJointandangles
	//effect range determine how far effect the change
	// eg. 0 just change the n th joint, 1 affect both next joint.
	int[][] changeJointandblength(int[][] jointarray, int n, int[] xy, int effectrange)
	{
		jointarray[n]=new int[]{xy[0],xy[1]};
		//anterior and posterior fixed point. head and tail shuldn't move
		// if the moving joint is between head and tail
		int afixedpointindex=n-effectrange-1;
		int pfixedpointindex=n+effectrange+1;
		if(afixedpointindex<0)
			afixedpointindex=0;
		if(pfixedpointindex>jointarray.length-1)
			pfixedpointindex=jointarray.length-1;
		//IJ.log("fixedpoint a "+String.valueOf(afixedpointindex)
		//		+" p "+String.valueOf(pfixedpointindex));
		int[] afixedpoint= new int[]{jointarray[afixedpointindex][0],jointarray[afixedpointindex][1]};
		int[] bfixedpoint= new int[]{jointarray[pfixedpointindex][0],jointarray[pfixedpointindex][1]};
		double axstep=(double)(jointarray[n][0]-jointarray[afixedpointindex][0])/(effectrange+1);
		double aystep=(double)(jointarray[n][1]-jointarray[afixedpointindex][1])/(effectrange+1);
		double pxstep=(double)(jointarray[pfixedpointindex][0]-jointarray[n][0])/(effectrange+1);
		double pystep=(double)(jointarray[pfixedpointindex][1]-jointarray[n][1])/(effectrange+1);
		
		for(int i=1;i<n-afixedpointindex;i++)
		{
			jointarray[afixedpointindex+i][0]=(int)(jointarray[afixedpointindex][0]+axstep*i);
			jointarray[afixedpointindex+i][1]=(int)(jointarray[afixedpointindex][1]+aystep*i);
		}
		for(int i=1;i<pfixedpointindex-n;i++)
		{
			jointarray[n+i][0]=(int)(jointarray[n][0]+pxstep*i);
			jointarray[n+i][1]=(int)(jointarray[n][1]+pystep*i);
		}
		return jointarray;
	}
	
	//change also neighboring joint angle
	//not implemented yet
	int[][] changeJointandangles(int[][] jointarray, int n, int[] xy, int effectrange)
	{
		jointarray[n]=new int[]{xy[0],xy[1]};
		
		
		return jointarray;

	}
	
	//calculate pixvalue/area of model density?
	//double[] calcPicdensity(int[][] jointarray)
	double[] calcPixvalArea(int[][] jointarray, double[] widtharray_, int start, int end)
	{
		double sumofvalue=0;
		double sumofarea=0;
		//System.arraycopy(joint, 0, tempjoint, 0, joint.length);
		for(int i=start;i<end;i++)
		{
			PolygonRoi anroi=getAnarea(i,jointarray,widtharray_);
			
			imp.setRoi(anroi);
			//Integrated Density is total sum of intensity of each pixcel, but seems not available?
			ImageStatistics ims=imp.getStatistics(ij.measure.Measurements.MEAN);
			//try{Thread.sleep(1000);}catch(InterruptedException e){}
			sumofvalue=sumofvalue+ims.mean*ims.area;
			sumofarea=sumofarea+ims.area;
			imp.killRoi();
		}
		//IJ.log("sumof value/area "+String.valueOf(sumofvalue/sumofarea));
		return new double[] {sumofvalue, sumofarea};
	}
	
	double[] calcPixvalArea(int[][] jointarray, double[] widtharray_)
	{
		return calcPixvalArea(jointarray, widtharray_, 0, jointarray.length-1);
	}
	double[] calcPixvalArea(int[][] jointarray)
	{
		return calcPixvalArea(jointarray, widtharray);
	}
	
	//make roi of one segment having width. the n is borne index
	PolygonRoi getAnarea(int n, int[][] jointarray, double[] widtharray_)
	{
		//int[] bornvector=new int[]{jointarray[n+1][0]-jointarray[n][0],jointarray[n+1][1]-jointarray[n][1]};
		//float[] verticalveca=new float[]{-bornvector[1],bornvector[0]};
		//float[] verticalvecb=new float[]{bornvector[1],-bornvector[0]};
		//calc vertical line at joint. using neigboring borne.
		int preindex=n-1;
		int postindex=n+2;
		if(n==0)
			preindex=0;
		if(n==jointarray.length-2)
			postindex=n+1;
		int[] bornvectora=new int[]{jointarray[n+1][0]-jointarray[preindex][0],
				jointarray[n+1][1]-jointarray[preindex][1]};//use anterior joint
		int[] bornvectorp=new int[]{jointarray[postindex][0]-jointarray[n][0],
				jointarray[postindex][1]-jointarray[n][1]};//posterior
		float[] verticalveca=new float[]{-bornvectora[1],bornvectora[0]};
		float[] verticalvecp=new float[]{-bornvectorp[1],bornvectorp[0]};
		float verticalvecsizea=
			(float)Math.sqrt(Math.pow(verticalveca[0],2)+Math.pow(verticalveca[1],2));
		float verticalvecsizep=
			(float)Math.sqrt(Math.pow(verticalvecp[0],2)+Math.pow(verticalvecp[1],2));
		float x1=(float)(verticalveca[0]/verticalvecsizea*widtharray_[n]);
		float x2=(float)(verticalvecp[0]/verticalvecsizep*widtharray_[n+1]);
		float x3=(float)(-verticalvecp[0]/verticalvecsizep*widtharray_[n+1]);
		float x4=(float)(-verticalveca[0]/verticalvecsizea*widtharray_[n]);
		float y1=(float)(verticalveca[1]/verticalvecsizea*widtharray_[n]);
		float y2=(float)(verticalvecp[1]/verticalvecsizep*widtharray_[n+1]);
		float y3=(float)(-verticalvecp[1]/verticalvecsizep*widtharray_[n+1]);
		float y4=(float)(-verticalveca[1]/verticalvecsizea*widtharray_[n]);
		float[] xarray = new float[]{x1+jointarray[n][0], x2+jointarray[n+1][0],
				x3+jointarray[n+1][0],x4+jointarray[n][0]};
		float[] yarray = new float[]{y1+jointarray[n][1], y2+jointarray[n+1][1],
				y3+jointarray[n+1][1],y4+jointarray[n][1]};
		return new PolygonRoi(xarray,yarray,4,Roi.POLYGON);
	}
	PolygonRoi getAnarea(int n, int[][] jointarray)
	{
		return getAnarea(n, jointarray, widtharray);
	}

	

	void calcOutlinpos(int[][] jointarray, double[] widtharray_)
	{
		routlinedpoints=new double[jointarray.length][2];
		loutlinedpoints=new double[jointarray.length][2];
		for(int i=0; i<jointarray.length;i++)
		{
			
			int antindex=i-1;
			int postindex=i+1;
			if(i==0)
				antindex=0;
			if(i==jointarray.length-1)
				postindex=i;
			int[] bornvectora=
				new int[]{jointarray[postindex][0]-jointarray[antindex][0],
					jointarray[postindex][1]-jointarray[antindex][1]};
			float[] verticalveca=new float[]{-bornvectora[1],bornvectora[0]};
			float verticalvecsizea=
				(float)Math.sqrt(Math.pow(verticalveca[0],2)+
						Math.pow(verticalveca[1],2));
			float x1=(float)(verticalveca[0]/verticalvecsizea*widtharray_[i]);
			float x4=(float)(-verticalveca[0]/verticalvecsizea*widtharray_[i]);
			float y1=(float)(verticalveca[1]/verticalvecsizea*widtharray_[i]);
			float y4=(float)(-verticalveca[1]/verticalvecsizea*widtharray_[i]);
			loutlinedpoints[i][0]=(double)x1+jointarray[i][0];
			loutlinedpoints[i][1]=(double)y1+jointarray[i][1];
			routlinedpoints[i][0]=(double)x4+jointarray[i][0];
			routlinedpoints[i][1]=(double)y4+jointarray[i][1];
		}
	}
	
	//calculate angle of each joint. 
	//actually this angle is difference between two vector;
	//avec=i-1 to i, bvec=i to i+1
	double[] calcAngles(int[][] jointarray)
	{
		double[] angles_=new double[jointarray.length];
		for(int i=1;i<angles_.length-1;i++)
		{
			angles_[i]=
				MathEx.getAngleDiff(jointarray[i][0]-jointarray[i-1][0], 
						jointarray[i][1]-jointarray[i-1][1],
						jointarray[i+1][0]-jointarray[i][0], 
						jointarray[i+1][1]-jointarray[i][1]);
			//IJ.log(String.valueOf(angles_[i]));
		}
		return angles_;
	}
	double[] calcAngles()
	{
		return calcAngles(joint);
	}
	
	//check if n-1,n,n+1 angles are less than angle liminit
	//ok true
	boolean checkAnglelimit(int[][] jointarray, int n)
	{
		double[] angles_=calcAngles(jointarray);
		boolean returnval=true;
		if(n==0)
		{
			if(Math.abs(angles_[n])>anglelimit||Math.abs(angles_[n+1])>anglelimit)
				returnval=false;
		}
		else if(n==angles_.length-1)
		{
			if(Math.abs(angles_[n-1])>anglelimit||Math.abs(angles_[n])>anglelimit)
				returnval=false;
		}
		else
		{
			if(Math.abs(angles_[n-1])>anglelimit
					||Math.abs(angles_[n])>anglelimit
					||Math.abs(angles_[n+1])>anglelimit)
				returnval=false;
		}
		return returnval;
	}
	
	//determine head or tail direction
	//ht;0=head, 1=tail
	void swing(int ht,double angle)
	{
		//tempjoint[]
		
	}
	
	//fit sine curve. somehow...
	//f(x)=a*sin(b*x+c) (+d was omitted for now)
	//scan each coefficient following range by 1/10 step at first
	// 0<a<(ymax-ymin)*2
	// 0<b<6/x.length*PI ; upto 3 cycle=3x2PI
	// 0<c<2PI/b=1/3*x.length
	// vector is angle of joints
	double[][] fitSine(double[] vector, double[] arange, double[] brange, double[] crange, int step)
	{
		double[] yrange=MathEx.range(vector);
		int xlength=vector.length;
		double a,b,c,mina,minb,minc;
		a=arange[0];
		b=brange[0];
		c=crange[0];
		mina=arange[0];
		minb=brange[0];
		minc=crange[0];
		double astep, bstep, cstep;
		astep=(arange[1]-arange[0])/step;
		bstep=(brange[1]-brange[0])/step;
		cstep=(crange[1]-crange[0])/step;
		double[] ysquare=MathEx.square(vector);
		double minerror=MathEx.sum(ysquare);
		double[] tempvec=new double[vector.length];
		double[] minvec=new double[vector.length];
		for(int i=1;i<=step;i++)
		{
			for(int j=1;j<=step;j++)
			{
				for(int k=1;k<=step;k++)
				{
					double[] errorvector=new double[vector.length];
					for(int l=0; l<vector.length;l++)
					{
						tempvec[l]=a*Math.sin(b*l+c);
						errorvector[l]=Math.pow(vector[l]-tempvec[l],2);
					}
					double temperror=MathEx.sum(errorvector);
					if(minerror>temperror)
					{
						minerror=temperror;
						mina=a;
						minb=b;
						minc=c;
						System.arraycopy(tempvec, 0, minvec, 0, tempvec.length);
						/*IJ.log("min "+String.valueOf(minerror)+" "+
								String.valueOf(a)+" "+
								String.valueOf(b)+" "+
								String.valueOf(c));
								*/
					}
					c=crange[0]+cstep*k;
				}
				c=crange[0];
				b=brange[0]+bstep*j;
			}
			b=brange[0];
			a=arange[0]+astep*i;
		}
		double[][] returnval=new double[4][];
		returnval[0]=minvec;
		returnval[1]=new double[]{mina};
		returnval[2]=new double[]{minb};
		returnval[3]=new double[]{minc};
		return returnval;
	}
	
	//shift; negative is forward locomotion. unit is segment?
	void propelModel(double[][] fittedresult, double shift)
	{
		double a=fittedresult[1][0];
		double b=fittedresult[2][0];
		double c=fittedresult[3][0];
		//double[] range = MathEx.range(angles);
		double[] centerpos=MathEx.mean(joint);
		//if move forward, head position has to be determined by
		//something. fit sin to the angles and predict next would better
		//
		double[] newangles=new double[angles.length];
		for(int i=1; i<newangles.length-1;i++)
		{
			newangles[i]=a*Math.sin(b*i+c+shift);
		}
		double absceilshift=Math.ceil(Math.abs(shift));
		double segmentlength=bodylength/bornnum;
		int[][] newjoint= new int[joint.length+(int)absceilshift][2];
		if(shift<0)//forward
		{
			//System.arraycopy(joint, 0, newjoint, (int)absceilshift, joint.length);
			deepcopyJoint(joint, 0, newjoint, (int)absceilshift,joint.length);
			for(int i=0; i<absceilshift; i++)
			{
				double angleatjoint=a*Math.sin(b*(-i)+c);
				int[] vectortorot=new int[]{
						newjoint[(int)absceilshift-i][0]-newjoint[(int)absceilshift+1-i][0],
						newjoint[(int)absceilshift-i][1]-newjoint[(int)absceilshift+1-i][1]};
				double veclength=
					Math.sqrt(Math.pow(vectortorot[0],2)+Math.pow(vectortorot[1],2));
				double[] vectortorotscaled=new double[]{
					(double)vectortorot[0]/veclength*segmentlength,
					(double)vectortorot[1]/veclength*segmentlength};
				double[] newpoint=MathEx.rotVec(vectortorotscaled,-angleatjoint);
				newjoint[(int)absceilshift-1-i]=
					new int[]{newjoint[(int)absceilshift-i][0]+(int)newpoint[0],
						newjoint[(int)absceilshift-i][1]+(int)newpoint[1]};
				IJ.log(String.valueOf(newjoint[(int)absceilshift-1-i][0])+" "+
						String.valueOf(newjoint[(int)absceilshift-1-i][1]));
			}
			//System.arraycopy(newjoint, 0, tempjoint,0,joint.length);
			deepcopyJoint(newjoint,0,tempjoint,0,joint.length);
		}
		else
		{
			//System.arraycopy(joint, 0, newjoint, 0, joint.length);
			deepcopyJoint(joint, newjoint);
			for(int i=0; i<absceilshift; i++)
			{
				double angleatjoint=a*Math.sin(b*(i+angles.length-1)+c);
				int[] vectortorot=new int[]{
						newjoint[joint.length-1+i][0]-newjoint[joint.length-1-1+i][0],
						newjoint[joint.length-1+i][1]-newjoint[joint.length-1-1+i][1]};
				double veclength=
					Math.sqrt(Math.pow(vectortorot[0],2)+Math.pow(vectortorot[1],2));
				double[] vectortorotscaled=new double[]{
					(double)vectortorot[0]/veclength*segmentlength,
					(double)vectortorot[1]/veclength*segmentlength};
				double[] newpoint=MathEx.rotVec(vectortorotscaled,angleatjoint);
				newjoint[joint.length-1+1+i]=
					new int[]{newjoint[joint.length-1+i][0]+(int)newpoint[0],
						newjoint[joint.length-1+i][1]+(int)newpoint[1]};
				IJ.log(String.valueOf(newjoint[joint.length-1+1+i][0])+" "+
						String.valueOf(newjoint[joint.length-1+1+i][1]));
			}
			//System.arraycopy(newjoint, (int)absceilshift, tempjoint,0,joint.length);
			deepcopyJoint(newjoint, (int)absceilshift, tempjoint,0,joint.length);
		}
	}
	
	void deepcopyJoint(int[][]srcjoint, int[][]destjoint)
	{
		deepcopyJoint(srcjoint, 0, destjoint, 0, srcjoint.length);
	}
	void deepcopyJoint(int[][]srcjoint, int srcindex, int[][]destjoint, int destindex, int length)
	{
		for(int i=0;i<length;i++)
		{
			destjoint[i+destindex][0]=srcjoint[i+srcindex][0];
			destjoint[i+destindex][1]=srcjoint[i+srcindex][1];
		}
		
	}

	// The head tail detection could be failed. 
	// used this method to reverse haed and tail
	void swapHT()
	{
		int[][] tempjoint_=new int[joint.length][2];
		deepcopyJoint(joint, tempjoint_);
		double[] tempwidtharray_=new double[widtharray.length];
		System.arraycopy(widtharray, 0, tempwidtharray_, 0, widtharray.length);
		for(int i=0; i<joint.length; i++)
		{
			joint[i][0]=tempjoint_[joint.length-1-i][0];
			joint[i][1]=tempjoint_[joint.length-1-i][1];
			widtharray[i]=tempwidtharray_[widtharray.length-1-i];
		}
		
	}
	
	void testmethod()
	{
		//System.out.println("test ");
		IJ.log("test ");
	}


}
