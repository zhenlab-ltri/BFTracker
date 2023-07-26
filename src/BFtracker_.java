// Copyright (C) 20120315 Taizo Kawano <tkawano at mshri.on.ca>
//
// This program is free software; you can redistribute it and/modify it 
// under the term of the GNU General Public License as published bythe Free Software Foundation;
// either version 2, or (at your option) any later version.
// 
// This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
// without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
// See the GNU General Public License for more details.
// 
// You should have received a copy of the GNU General Public License
// along with this file.  If not, write to the Free Software Foundation,
// 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
// 
// Use virtural stack taken by Realtimetracker_09 or after bright field full field images
// needs higer than imagej 1.44k
// 1. open virtual stack
// 2. start BFT plugin
// 3. click Setup and set threshold and roi size (min,max). this filed has to be hit return/enter key
// 4. choose scale 10x 4x 
// 5. Go
// 6. check if head and tail are detected correctly. 
// 7.if not, click swap botton to change all frame, 
//or click the graph to swap each period
//or click each slice image one by one.
// 8. output data and curvature. the unit of angles is radian
//120607 gave up to track coiling situation (touching head or tail to the body)
//could be detectable omegaturn by combination of reverse/deepbend/touching.
// just transferrig .jar file made by eclipse to plugin folder of imagej didn't work
// transfer /bin/*.class to plugin/BFT did work.



import java.util.*;
import java.util.prefs.Preferences;
import java.io.*;
import java.awt.*;
import java.awt.event.*;

//import OutlinedShape.OverThreshRegion;

import ij.*;
import ij.io.*;
import ij.process.*;
import ij.gui.*;
import ij.plugin.*;
import ij.plugin.filter.*;
import ij.plugin.frame.PlugInFrame; 
import ij.plugin.frame.ThresholdAdjuster;
import ij.measure.Calibration;
import ij.gui.PlotWindow;

public class BFtracker_ extends PlugInFrame  implements ActionListener,ImageListener,MouseListener, KeyListener,ItemListener{
    
    //globals just in this class
    //Preferences prefs;
    TextField minnumtext;
    TextField maxnumtext;
    TextField thresholdnumtext;
    java.awt.Choice scalechoice;
    
    
    ImagePlus imp;
    ImageProcessor ip;//ip changes when change imp slice
    ImagePlus tempimp;//tempimp also changes when change slice
    ImagePlus imp_work;
    
    ImageCanvas ic;
    ImageWindow iw;
    ImageCanvas plotic;
    ImageWindow plotiw;
    int slicenumber;
    int width;
    int height;
    int bitdepth;
    
    String dirforsave;
    boolean ready;
    
    String defdir;
    String shorttitle;
    
    ArrayList<Roi> preroiarraylist = null;
    //RoidatcorrectorThread(RoidatcorrectorThread tpf);
    RoidatcorrectorThread rt=null;
    
    
    //public vales. also used in RoidatcorrectorThread
    public int[] fieldvaluearray= {1000, 12000, 200};//min, max, threshold
    public int[] periodsdat={0};// having stable slice start and end indexes
    public double[][] trackeddata;//[slice index][feret, angle, min, feretx, ferety]
    public double[][] stagepos;//[slice index][xpos, ypos, zpos] in um?
    double scale=0.63*4;//0.63*4 with 10x objective bin 4. um/pix? 1.575*4 for 4x obj? 16x 0.39375*4
    double[][] globalpos;//stagepos + mid pos
    double[] velocity2;//using head middle tail of outline shape
    double[] velocity3;//using middle and neighbor joint of wormmodel
    double[] range;
    double[] xvector;
    double[] bendvec;
    double[] bendvecscaled;
    
    Roi[] trackedroi;//Need something hold roi data. List?
    public double[][][] headtailslicedata;
    public boolean[] roidetected;
    //public ArrayList<Roi> trackedroi;
    public double[] clickedxy;
   	double[] velocity;
   	//this is output data
   	double[] velocitywithna;
    
    //detection by roi shape. sharp and rouneder end and middle
   	OutlinedShape[] osarray;
    public int[][][] headtailslicedata2;
    double[][] sharpnessofheadandtail;//outlinedshape's sharpend and roundend sharpness
    
    //model
    Wormmodel[] wmarray;
    Wormmodel samplewm;
	double[] lengtharray;
	double medianlength;
	double[] sumwidtharray;
   	double[][] anglesarray;
   	boolean[] adequateflags;
   	ArrayList<Adequateperiod> adqplist;
   	ArrayList<Coiledperiod> coiledplist;
   	
	//plot related
	ImagePlus plotimp;
	Plot plot;
	PlotWindow pw;
	double plotmin;
	double plotmax;
	double plottedmin;
	double plottedmax;
	
	//manuall correction
	char key1='f';
	char key2='r';
	char undokey='z';
	int[] undodata=new int[4];//slice, head tail mid, xy
	boolean key1flag=false;
	boolean key2flag=false;
    
    void setUp()
    {
    	imp=WindowManager.getCurrentImage();
        slicenumber=imp.getNSlices();
    	osarray=new OutlinedShape[slicenumber];
    	wmarray=new Wormmodel[slicenumber];
    	globalpos=new double[slicenumber][2];
    	bendvec=new double[slicenumber];
    	bendvecscaled=new double[slicenumber];
        if(slicenumber==1)
        {
            IJ.log("slice number is 1. not stack");
            return;
        }
        ip=imp.getProcessor();//ip changes when change imp slice
        bitdepth=imp.getBitDepth();
        /*
        if(imp.getBitDepth()!=8)
        {
        	imp.setProcessor(ip.convertToByte(true));
        }
        */
        //tempimp=new ImagePlus("temp", ip);//tempimp also changes when change slice
        width=imp.getWidth();
        height=imp.getHeight();
    	plotmin=-width;
    	plotmax=width;
        //ImageWindow iw = imp.getWindow();
        //trackedroi=new ArrayList<Roi>(slicenumber); 
        //IJ.log("tracked roi size"+trackedroi.size());
        //toint=slicenumber;
        //fieldvaluearray[1]=slicenumber;
        //ic= iw.getCanvas();
        //ic.addMouseListener(this);
        defdir=IJ.getDirectory("image");
        //IJ.log("defdir");
        shorttitle=imp.getShortTitle();
        
        ImageStack stack = imp.getStack();
        String label = stack.getSliceLabel(imp.getCurrentSlice());
        //IJ.log(label);
        
        /*
        Wormmodel wm =new Wormmodel(1);
        wm.testmethod();
        if(true)return;
        */
        //if there is stable period data prepared with Shiftcheck plugin, get it.
        java.util.Properties prop=imp.getProperties();
        if(prop!=null)
        {
            IJ.log("prop is not null");
            String infostr=prop.getProperty("Info");
            if(infostr!=null)
            {
                IJ.log(infostr);
                String[] infoarray=infostr.split("\n|,");//split by \n. stack has original tiff name at 1st line.(virtual stack dont have)
                periodsdat=new int[infoarray.length];
                for(int i=0;i<infoarray.length;i++)
                {
                    //IJ.log(infoarray[i]);
                    periodsdat[i]=Integer.parseInt(infoarray[i]);
                    IJ.log(String.valueOf(periodsdat[i]));
                }
            }
        }
        else//no prop data
        {
            IJ.log("prop is null");
            periodsdat=new int[2];
            periodsdat[0]=1;
            periodsdat[1]=slicenumber-3;
        }
          
        //imp.setSlice(periodsdat[0]+1);
        imp.setSlice(1);
        //if(true)return;
        //addKeyListener(this);
        //requestFocus(); //may need for keylistener
        /*
        ImageProcessor ip_ori=ip.duplicate();
        ImageProcessor ip_work=ip.duplicate();
        imp_work=new ImagePlus("work", ip_work);
        
        RankFilters rf = new RankFilters();
        //rf.setup("median",imp);
        rf.rank(ip_work, 2, 4);// median 4
        */
        //ip_work.setAutoThreshold("Default",false,0);//did'nt work? why? with realtimetracker works
        //ip_work.autoThreshold();
        /*
        //wartersheding cut worm half.w
        EDM edm = new EDM();
        ip_work.invert();
        edm.toWatershed(ip_work);
        ip_work.invert();
        */
        /*
        imp_work.show();
        ThresholdAdjuster ta=new ThresholdAdjuster();
        fieldvaluearray[2]=(int)ip_work.getMaxThreshold();
        ImageWindow iw = imp_work.getWindow();
        ic= iw.getCanvas();
        ic.addMouseListener(this);
        */
        ip.setAutoThreshold("Triangle");
        ThresholdAdjuster ta=new ThresholdAdjuster();
        fieldvaluearray[2]=(int)ip.getMaxThreshold();
        thresholdnumtext.setText(String.valueOf(fieldvaluearray[2]));
        iw = imp.getWindow();
        //iw.addKeyListener(this);
        iw.addKeyListener(this);
        ic= iw.getCanvas();
        ic.addKeyListener(this);
        iw.removeKeyListener(IJ.getInstance());//need remove?
        ic.removeKeyListener(IJ.getInstance());
        //ic.addMouseListener(this);
        //ta.run();
        IJ.log("setup done");
        //IJ.log("Cleak near head");
    }
    
    //innerclass for keylistener
    //test because keylistner don't respond after processing -> still not work
    //after removing addMouseListener, worked fine. mouse listener conflict
    /*
    class Keychecker implements KeyListener
    {
    	BFtracker_ bft;
    	
    	Keychecker(BFtracker_ bft_)
    	{
    		bft=bft_;
    	}
        public void keyTyped(KeyEvent e) {
        	IJ.log("KEY TYPED: ");
        }
            
        public void keyPressed(KeyEvent e) {
        	IJ.log("KEY PRESSED: ");
        	//int c=e.getKeyCode();
        	char keychar = e.getKeyChar();
        	IJ.log(String.valueOf(keychar));
        	if(keychar==bft.key1)
        	{
        		bft.key1flag=true;
        		IJ.log("key1true");
        	}
        	else if(keychar==bft.key2)
        	{
        		bft.key2flag=true;		
        		IJ.log("key2true");
        	}
        	//key1
        }
            
        public void keyReleased(KeyEvent e) {
        	IJ.log("KEY RELEASED: ");

        	char keychar = e.getKeyChar();
        	IJ.log(String.valueOf(keychar));
        	if(keychar==bft.key1)
        	{
        		bft.key1flag=false;
        		IJ.log("key1false");
        	}
        	else if(keychar==key2)
        	{
        		bft.key2flag=false;		
        		IJ.log("key2false");
        	}
        }    	
    }
    */
    
    public BFtracker_()
    {
        super("BFtracker_");
        ImagePlus.addImageListener(this);
        trackeddata=null;
        
        
        //if(true)return;
        
        
        //Prepare GUI
        GridBagLayout gbl = new GridBagLayout();
        setLayout(gbl);
        GridBagConstraints gbc = new GridBagConstraints();
        
        Button b=new Button("Setup");
        b.setPreferredSize(new Dimension(100,30));
        b.addActionListener(this);
        gbc.gridx=0;
        gbc.gridy=0;
        gbc.gridwidth= 2;
        gbl.setConstraints(b,gbc);
        add(b);
        
        b=new Button("Go");
        b.setPreferredSize(new Dimension(100,30));
        b.addActionListener(this);
        gbc.gridx=0;
        gbc.gridy=1;
        gbc.gridwidth= 2;
        gbl.setConstraints(b,gbc);
        add(b);
        
        Label labelmin=new Label("Min");
        gbc.gridx=0;
        gbc.gridy=2;
        gbc.gridwidth= 1;
        gbl.setConstraints(labelmin,gbc);
        add(labelmin);
        
        minnumtext = new TextField(String.valueOf(fieldvaluearray[0]), 5);
        minnumtext.addActionListener(this);
        gbc.gridx=1;
        gbc.gridy=2;
        gbc.gridwidth= 1;
        gbl.setConstraints(minnumtext,gbc);
        add(minnumtext);
        
        Label labelmax=new Label("Max");
        gbc.gridx=0;
        gbc.gridy=3;
        gbc.gridwidth= 1;
        gbl.setConstraints(labelmax,gbc);
        add(labelmax);
        
        maxnumtext = new TextField(String.valueOf(fieldvaluearray[1]), 5);
        maxnumtext.addActionListener(this);
        gbc.gridx=1;
        gbc.gridy=4;
        gbc.gridwidth= 1;
        gbl.setConstraints(maxnumtext,gbc);
        add(maxnumtext);
        /*
        Label labelthreshold=new Label("Threashold");
        gbc.gridx=0;
        gbc.gridy=3;
        gbc.gridwidth= 1;
        gbl.setConstraints(labelthreshold,gbc);
        add(labelthreshold);
        */
        thresholdnumtext = new TextField(String.valueOf(fieldvaluearray[2]), 5);
        /*
        thresholdnumtext.addActionListener(this);
        gbc.gridx=1;
        gbc.gridy=3;
        gbc.gridwidth= 1;
        gbl.setConstraints(thresholdnumtext,gbc);
        add(thresholdnumtext);
        */
        
        scalechoice=new Choice();
        gbc.gridx=0;
        gbc.gridy=5;
        gbc.gridwidth= 2;
        scalechoice.add("10x");
        scalechoice.add("4x");
        scalechoice.add("16x");
        scalechoice.setPreferredSize(new Dimension(80, 20));
        scalechoice.addItemListener(this);
        gbl.setConstraints(scalechoice,gbc);
        add(scalechoice);
        
        //Button b2=new Button("PostFilter");
        Button b2=new Button("swap HT");
        b2.setPreferredSize(new Dimension(100,30));
        b2.addActionListener(this);
        gbc.gridx=0;
        gbc.gridy=6;
        gbc.gridwidth= 2;
        gbl.setConstraints(b2,gbc);
        add(b2);

        Button b3=new Button("Curvature");
        b3.setPreferredSize(new Dimension(100,30));
        b3.addActionListener(this);
        gbc.gridx=0;
        gbc.gridy=7;
        gbc.gridwidth= 2;
        gbl.setConstraints(b3,gbc);
        add(b3);
        
        Button b4=new Button("Output");
        b4.setPreferredSize(new Dimension(100,30));
        b4.addActionListener(this);
        gbc.gridx=0;
        gbc.gridy=8;
        gbc.gridwidth= 2;
        gbl.setConstraints(b4,gbc);
        add(b4);
        
        GUI.center(this);
        setSize(100,280);
        setVisible(true);
        
        
    }
    
    public void imageOpened(ImagePlus imp) {}
    public void imageUpdated(ImagePlus imp_)
    {
        //if(trackeddata!=null && trackeddata[imp.getSlice()]!=null&&trackedroi[imp.getSlice()]!=null)
        //if(trackeddata!=null&&rt!=null)//pre processed condition
        if(imp_.equals(imp)&&rt!=null&&wmarray!=null)//pre processed condition
        {
        	//IJ.log("wmarray not null");
        	if(rt.isAlive())//processing now
        	{
        		return;
        	}
            //IJ.log("image updated slice "+String.valueOf(imp.getSlice()));
            /*
            Overlay overlay= imp.getOverlay();
            if(overlay==null)
            {
            	overlay= new Overlay();
            }
            */
            //IJ.log(String.valueOf(trackedroi.size()));
            //if(trackedroi[imp.getSlice()]!=null)
            
            Overlay overlay= new Overlay();
            int frameindex=imp.getSlice()-1;
            //not good way, but this is checking if there is data
            if(headtailslicedata2[frameindex][2][0]!=0)
            {
            	//iw.removeKeyListener(this);
            	//iw.addKeyListener(this);
            	/*
            	KeyListener[] kl=iw.getKeyListeners();
            	IJ.log("kl "+String.valueOf(kl.length));
            	for(int i=0;i<kl.length;i++)
            	{
            		if(kl[i].equals(this))
            		{
            			IJ.log("contain this");
            		}
            		else
            		{
            			IJ.log("dont contain this");
            		}
            	}
            	*/
            	//overlay on image
            	//showoverlay(frameindex);
            	
            	
            	/*
                trackedroi[imp.getSlice()].setStrokeColor(Color.orange);
                overlay.add(trackedroi[imp.getSlice()]);
                //0 round, or head after manual correction
                OvalRoi roundendroi= new OvalRoi(headtailslicedata2[frameindex][0][0]-5,headtailslicedata2[frameindex][0][1]-5, 10,10);
                roundendroi.setStrokeColor(Color.red);
                overlay.add(roundendroi);
                //1 sharp, or tail
                TextRoi sharpendroi= new TextRoi(headtailslicedata2[frameindex][1][0]-5,headtailslicedata2[frameindex][1][1]-5, "X");
                sharpendroi.setStrokeColor(Color.blue);
                overlay.add(sharpendroi);
                
                OvalRoi midroi= new OvalRoi(headtailslicedata2[frameindex][2][0]-5,headtailslicedata2[frameindex][2][1]-5, 10,10);
                midroi.setStrokeColor(Color.green);
                overlay.add(midroi);
                */
                //directory use OutlinedShape to make plot
            	/*
                OutlinedShape tempos=osarray[frameindex];
                plot=new Plot("plot","x","deltaangle",new double[]{0},new double[]{0});
                plot.setLimits(0,tempos.npoints,-tempos.deltaanglethreshold,tempos.deltaanglethreshold*4);
        		plot.setColor(Color.black);
                plot.addPoints(tempos.indexarray,tempos.smanglediff,2);
                
                ArrayList<OutlinedShape.OverThreshRegion> ootrarray=tempos.otrarraylist;
                
                OutlinedShape.OverThreshRegion ootr1=ootrarray.get(0);
                OutlinedShape.OverThreshRegion ootr2=ootrarray.get(1);
                IJ.log("1 "+String.valueOf(ootr1.startindex)+"hightperwith " + String.valueOf(ootr1.hightperwidth));
        		plot.setColor(Color.blue);
        		double[] xindexies= new double[ootr1.width];
        		double[] yvalues= new double[ootr1.anglediffarray.size()];
        		for(int i=0;i<ootr1.width;i++)
        		{
        			xindexies[i]=(double)ootr1.index.get(i).intValue();
        			yvalues[i]=ootr1.anglediffarray.get(i).doubleValue();

        		}
        		plot.addPoints(xindexies,yvalues,2);//circle 0, dot 6. line is 2
                plot.addPoints(new double[]{(double)ootr1.maxindex}, 
                		new double[]{(double)ootr1.maxvalue}, 0);
                
                IJ.log("2 hightperwith" + String.valueOf(ootr2.hightperwidth));
        		plot.setColor(Color.red);
        		xindexies= new double[ootr2.width];
        		yvalues= new double[ootr2.anglediffarray.size()];
        		for(int i=0;i<ootr2.width;i++)
        		{
        			xindexies[i]=(double)ootr2.index.get(i).intValue();
        			yvalues[i]=ootr2.anglediffarray.get(i).doubleValue();

        		}
        		plot.addPoints(xindexies,yvalues,2);//circle 0, dot 6. line is 2
        		
                plot.addPoints(new double[]{(double)ootr2.maxindex}, 
                		new double[]{(double)ootr2.maxvalue}, 0);
                pw.drawPlot(plot);
                */
            	
            	//plotVel();
            }
            else
            {
            	overlay.add(new Roi(1,1,1,1));//clear against null cause error? create dummy roi
            	overlay.clear();            	
            }
            
            if(wmarray[frameindex]!=null)
            {
            	IJ.log("show wm "+String.valueOf(frameindex));
            	wmarray[frameindex].showShape(imp, adequateflags[frameindex]);
            }
            
        	ImageProcessor plotip = plotVectordata();
            plot.drawLine((double)imp.getSlice(), plottedmin, (double)imp.getSlice(),plottedmax );
            plotip=plot.getProcessor();
            plotimp.setProcessor(null,plotip);

            //imp.setOverlay(overlay);                
            
            /*
            Overlay overlay= new Overlay();
            if(roidetected[imp.getSlice()]==true)
            {
                IJ.log("roidetected true");
                trackedroi[imp.getSlice()].setStrokeColor(Color.orange);
                overlay.add(trackedroi[imp.getSlice()]);
                if(headtailslicedata[imp.getSlice()]!=null)
                {
                    IJ.log("headtailslicedata not null");
                    
                    OvalRoi headcircleroi= new OvalRoi((int)headtailslicedata[imp.getSlice()][0][0]-2,(int)headtailslicedata[imp.getSlice()][0][1]-2, 5,5);
                    headcircleroi.setStrokeColor(Color.red);
                    overlay.add(headcircleroi);
                }
                //imp_work.setOverlay(overlay);
                //imp.setOverlay(overlay);
            }
            else
            {
            	overlay.add(new Roi(1,1,1,1));//clear against null cause error? create dummy roi
            	overlay.clear();
            }
            imp.setOverlay(overlay);
            */
        }
    }
    public void imageClosed(ImagePlus impc) 
    {
        //Write logic to clear valables when image closed here 
        if(imp==impc)
        {
            //IJ.log("The image closed");
            imp=null;
            iw.removeKeyListener(this);
            ic.removeKeyListener(this);
            ic.removeMouseListener(this);
            plotic.removeMouseListener(this);
            trackeddata=null;
            trackedroi=null;
            rt=null;
            osarray=null;
            wmarray=null;
            headtailslicedata2=null;
            adqplist=null;

        }
        else
        {
            //IJ.log("something else closed");
        }
    }
    //show overlay rois of outline head tail mid on image
	void showoverlay(int n)
	{
        Overlay tempoverlay= new Overlay();
		trackedroi[n+1].setStrokeColor(Color.orange);
		tempoverlay.add(trackedroi[n+1]);
		//0 round, or head after manual correction
		OvalRoi roundendroi= new OvalRoi(headtailslicedata2[n][0][0]-5,headtailslicedata2[n][0][1]-5, 10,10);
		roundendroi.setStrokeColor(Color.red);
		tempoverlay.add(roundendroi);
		//1 sharp, or tail
		TextRoi sharpendroi= new TextRoi(headtailslicedata2[n][1][0]-5,headtailslicedata2[n][1][1]-5, "X");
		sharpendroi.setStrokeColor(Color.blue);
		tempoverlay.add(sharpendroi);

		OvalRoi midroi= new OvalRoi(headtailslicedata2[n][2][0]-5,headtailslicedata2[n][2][1]-5, 10,10);
		midroi.setStrokeColor(Color.green);
		tempoverlay.add(midroi);    
		imp.setOverlay(tempoverlay); 
	}
	
	
    public void itemStateChanged(ItemEvent e) {
        int selectedindex=0;
        Choice cho = (Choice)e.getSource();
        selectedindex=cho.getSelectedIndex();
        IJ.log("index "+String.valueOf(selectedindex));
        if(e.getSource()==scalechoice)
        {
        	if(selectedindex==0)
        		scale=0.63*4;//0.63*4 with 10x objective bin 4. um/pix? 1.575*4 for 4x obj?;
        	else if(selectedindex==1)
        		scale=1.575*4;
        	else if(selectedindex==2)
        		scale=0.3937*4;
        }
    }
    
    
	
    boolean checkTextField(TextField tf, int index)//return true if it ok. the index 0=from 1=to, 2=interleave
    {
        int testint;
        try
        {
            testint = Integer.parseInt(tf.getText());
            fieldvaluearray[index]=testint;
            return true;
        } catch (Exception ex) {
            IJ.log("It is not valid as a number.");
            return false;
        }
    }
    
    
    public void actionPerformed(ActionEvent e)
    {
        String lable =e.getActionCommand();
        IJ.log(lable);
        if(lable.equals(minnumtext.getText()))//from num has changed
        {
            boolean intcheck=checkTextField(minnumtext, 0);
            if(!intcheck)
            {
                minnumtext.setText(String.valueOf(fieldvaluearray[0]));
            }
            IJ.log("from "+String.valueOf(fieldvaluearray[0]));
        }
        else if(lable.equals(maxnumtext.getText()))//to num has changed
        {
            boolean intcheck=checkTextField(maxnumtext, 1);
            if(!intcheck)
            {
                maxnumtext.setText(String.valueOf(fieldvaluearray[1]));
            }
            IJ.log("to "+String.valueOf(fieldvaluearray[1]));
        }
        else if(lable.equals(thresholdnumtext.getText()))//interleave num has changed
        {
            boolean intcheck=checkTextField(thresholdnumtext, 2);
            if(!intcheck)
            {
                thresholdnumtext.setText(String.valueOf(fieldvaluearray[2]));
            }
            else if(Integer.parseInt(thresholdnumtext.getText())>slicenumber)
            {
                IJ.log("It is too large.");
                thresholdnumtext.setText(String.valueOf(fieldvaluearray[2]));
            }
            IJ.log("interleave "+String.valueOf(fieldvaluearray[2]));
        }
        else//any botton pushed
        {
            if (lable.equals("Setup"))
            {
                wmarray=null;
                trackeddata=null;
                trackedroi=null;
                IJ.log("Setup");
                if(ic!=null)
                {
                	ic.removeMouseListener(this);
                }
                if(WindowManager.getCurrentImage()!=null)
                {
                	setUp();
                }
                
            }
            else if(lable.equals("Go"))
            {
                IJ.log("label.equals go");
            	//iw.removeKeyListener(IJ.getInstance()); 
            	fieldvaluearray[2]=(int)ip.getMaxThreshold();
            	//iw.removeKeyListener(this);
            	//ic.removeKeyListener(this);
                //ic.removeMouseListener(this);
                
                //ImageWindow iw = imp.getWindow();
                //ic= iw.getCanvas();
                //ic.addMouseListener(this);
                //iw.addKeyListener(this);//this need to use keylistener after clicking? strange bug?
                IJ.log("pre rt instancing");
                rt= new RoidatcorrectorThread(this);
                IJ.log("pre rt.start");
                rt.start();
            	
            }
            else if (lable.equals("Plot"))
            {
                //if(trackeddata!=null && trackeddata[imp.getSlice()]!=null&&trackedroi[imp.getSlice()]!=null)
                if(trackeddata!=null)
                {
                    IJ.log("datas are exist");
                    //plottting
                    double[] dummy={0};
                    Plot plot=new Plot("plot","slicenum","velocity",dummy,dummy);
                    plot.setLimits(0,slicenumber,-width/10,width/10);
                    String[] velocitystring=calVelString();
                    for(int i=0; i<slicenumber;i++)
                    {
                    	if(!velocitystring[i].equals("NA"))
                    	{
                            //IJ.log("velocitystring[i] is not NA at  "+String.valueOf(i));
                            int startindex=i;
                            int endindex;
                            while(!velocitystring[i].equals("NA"))
                            {
                            	//IJ.log(velocitystring[i]);
                                i++;
                            }
                            //IJ.log("velocitystring[i] is NA at  "+String.valueOf(i));
                            endindex=i-1;
                        //if(endindex-startindex>0)
                        //{
                            double[] xindex=new double[endindex-startindex+1];
                            double[] plottingvector=new double[endindex-startindex+1];
                            for(int j=0; j<endindex-startindex+1;j++)
                            {
                                xindex[j]=startindex+j+1;//+1 to make correspond with slicenumber
                                plottingvector[j]=Double.valueOf(velocitystring[startindex+j]);
                            }
                            plot.setColor(Color.red);
                            plot.addPoints(xindex,plottingvector,2);
                            //IJ.log("added "+String.valueOf(endindex-startindex)+" to plot");
                        //}
                    	}
                    	
                    }
                    plot.show();
                    
                }
                
            }
            else if(lable.equals("PostFilter"))
            {
            	if(wmarray!=null)
            	{
            		filteringWm();
            	}
            
            }
            else if(lable.equals("swap HT"))
            {
            	/*if(key1flag)//u
            	{
            		//do something
            	}
            	*/
              	IJ.log("swap all head tail ");
              	for(int i=0;i<slicenumber;i++)
              	{
              		wmarray[i].swapHT();
              		anglesarray[i]=wmarray[i].calcAngles();
              	}
              	velocity3=calVelocity3();
            	ImageProcessor plotip = plotVectordata();
                plot.drawLine((double)imp.getSlice(), plottedmin, (double)imp.getSlice(),plottedmax );
                plotip=plot.getProcessor();
                plotimp.setProcessor(null,plotip);
              	
              	wmarray[imp.getSlice()].showShape(imp, adequateflags[imp.getSlice()]);
            }
            else if(lable.equals("Curvature"))
            {
            	if(anglesarray!=null)
            	{
            		double[] curvarray=new double[31*slicenumber];
            		for(int i=0; i<slicenumber;i++)
            		{
            			if(adequateflags[i])
            			{
            				for(int j=0;j<31;j++)
            				{
            					curvarray[i*31+j]=anglesarray[i][j+1];
            				}
            			}
            			else
            			{
            				for(int j=0;j<31;j++)
            				{
            					curvarray[i*31+j]=10;
            				}
            			}
            			
            		}
            		ImageProcessor curvip=new FloatProcessor(31, slicenumber, curvarray);
            		byte[] red=new byte[256];
            		byte[] green=new byte[256];
            		byte[] blue=new byte[256];
            		red[255]=(byte)255;
            		green[255]=(byte)255;
            		blue[255]=(byte)255;
            		for(int i=0;i<255;i++)
            		{
            			if(i<64)
            			{
            				red[i]=(byte)255;
            				green[i]=(byte)(256/64*i);
            			}
            			else if(i<128)
            			{
            				red[i]=(byte)(255-256/64*(i-64));
            				green[i]=(byte)255;
            			}
            			else if(i<192)
            			{
            				green[i]=(byte)255;
            				blue[i]=(byte)(256/64*(i-128));
            			}
            			else
            			{
            				blue[i]=(byte)255;
            				green[i]=(byte)(255-256/64*(i-192));
            			}
            		}
            		java.awt.image.IndexColorModel icm = 
            			new java.awt.image.IndexColorModel(8,256,red,green,blue);
            		//LUT curvlut=new LUT(red, green, blue);
            		curvip.setColorModel(icm);
            		curvip.setInterpolationMethod(curvip.NONE);
            		ImageProcessor curvipresized=curvip.resize(31*10,slicenumber,false).rotateLeft();
            		
            		curvipresized.setMinAndMax(-1,1);
            		ImagePlus curvimp=new ImagePlus(shorttitle+"curvature",curvipresized);
            		//cuvimp.a
            		curvimp.show();
            	}
            	
            }
            else if (lable.equals("Output"))
            {
                if(wmarray!=null)
                {
                	outPutdata2();
                	
	                //output as text
                	/*
	                String strforsave;
	                String header="";
	                String BR = System.getProperty("line.separator");
	                
	                //header preparation
	                //1st line has rois width height
	                for(int i=0; i<periodsdat.length;i++)
	                {
	                    String periodsstr=String.valueOf(periodsdat[i]);
	                    
	                    header=header+periodsstr+",";
	                }
	                header=header+BR;
	                //2nd line 9 elements
	                //header=header+"velocity, head_x, head_y, tail_x, tail_y, x, y, feret_max, feret_min";
	                header=header+"xpos, ypos, zpos, velocity, head_x, head_y, tail_x, tail_y, x, y, feret_max, feret_min";
	                //header=header+BR;
	                strforsave=header+BR;
	                StringBuffer strbuff=new StringBuffer();
	                String aslicestring="";
	                
	                //new new version
	                String[] velocitystring=calVelString();
	                
	                for(int i=0;i<slicenumber;i++)
	                {
	                	strbuff.append(String.valueOf(stagepos[i+1][0])+","+String.valueOf(stagepos[i+1][1])+","+String.valueOf(stagepos[i+1][2])+",");
	                	strbuff.append(velocitystring[i]+","+prepRoidataforOutput(i+1)+BR);
	                }
	
	                
	                strforsave=strforsave+strbuff.toString();
	                //SaveDialog(java.lang.String title, java.lang.String defaultDir, java.lang.String defaultName, java.lang.String extension)
	                SaveDialog sd=new SaveDialog("save data", defdir, shorttitle, "");
	                String chosendir=sd.getDirectory();
	                String chosenfilename=sd.getFileName();
	                if(chosenfilename==null)//canceled
	                {
	                	return;
	                }
	                IJ.saveString(strforsave,chosendir+chosenfilename+".txt");//save data into same dir name as imagetitle.txt
	                IJ.log("Output is saved in; "+chosendir);
	                */
                }
            }
        }
    }
    
    
    public void keyTyped(KeyEvent e) {
    	//IJ.log("KEY TYPED: ");
    }
        
    public void keyPressed(KeyEvent e) {
    	//IJ.log("KEY PRESSED: ");
    	//int c=e.getKeyCode();
    	char keychar = e.getKeyChar();
    	//IJ.log(String.valueOf(keychar));
    	if(keychar==key1)
    	{
    		key1flag=true;
    		//IJ.log("key1true");
    	}
    	else if(keychar==key2)
    	{
    		key2flag=true;		
    		//IJ.log("key2true");
    	}
    	else if(keychar==undokey)
    	{
    		IJ.log("undo to "+String.valueOf(undodata[0])+" "+String.valueOf(undodata[1])+" "+String.valueOf(undodata[2])+" "+String.valueOf(undodata[3]));
    		//char undokey='z';
    		//changeTargetpos(int frameindex, int feature, int[] xypos)//index start0, head tail mid, xy
    		changeTargetpos(undodata[0],undodata[1], new int[] {undodata[2],undodata[3]});
    		//int[] undodata=new int[4];//slice, head tail mid, xy
    	}
    	else//if it is other keys
    	{
    		IJ.getInstance().keyPressed(e);//pass the key event to IJ
    	}
    }
        
    public void keyReleased(KeyEvent e) {
    	//IJ.log("KEY RELEASED: ");

    	char keychar = e.getKeyChar();
    	//IJ.log(String.valueOf(keychar));
    	if(keychar==key1)
    	{
    		key1flag=false;
    		//IJ.log("key1false");
    	}
    	else if(keychar==key2)
    	{
    		key2flag=false;		
    		//IJ.log("key2false");
    	}
    }
    
    
    //mouse event    
    public void mouseClicked(MouseEvent e) {
        //IJ.log("clicked");
        //get clicked xy order
        Point cursorpoint=ic.getCursorLoc();
        clickedxy=new double[] {(double)cursorpoint.x, (double)cursorpoint.y};
        int[] clickedxyint = new int[]{cursorpoint.x, cursorpoint.y};
        Point plotcursorpoint=plotic.getCursorLoc();
		int frameindex=imp.getSlice()-1;
    	if(imp==WindowManager.getCurrentImage())
    	{
    		IJ.log("clicked at "+String.valueOf(cursorpoint.x)+ " " +String.valueOf(cursorpoint.y));

    		//iw.removeKeyListener(this);
    		//start tracking
    		//if(trackeddata==null)
    		//{
    		/*
        	fieldvaluearray[2]=(int)ip.getMaxThreshold();
        	//iw.removeKeyListener(this);
            //ic.removeMouseListener(this);

            //ImageWindow iw = imp.getWindow();
            //ic= iw.getCanvas();
            //ic.addMouseListener(this);
            //iw.addKeyListener(this);//this need to use keylistener after clicking? strange bug?
            rt= new RoidatcorrectorThread(this);
            rt.start();
    		 */
    		//}
    		//else if(trackeddata!=null && trackeddata[imp.getSlice()]!=null&&trackedroi[imp.getSlice()]!=null)
    		//if(wmarray!=null)
    		//{
    		//IJ.log("clicked after process");
    		/*if(headtailslicedata2[imp.getSlice()-1]!=null)
            	{
            	//here new method to correct head tail and middle.
            	//don't work since mouselistener conflict with keylistener?
            	//try again

            	if(key1flag)//u
            	{
            		//changeTargetpos(int frameindex, int feature, int[] xypos)//index start0, head tail mid, xy
            		changeTargetpos(imp.getSlice()-1,0,clickedxyint);
            		//headtailslicedata2[imp.getSlice()-1][0]=clickedxyint;//head
            	}
            	else if(key2flag)//j
            	{
            		changeTargetpos(imp.getSlice()-1,1,clickedxyint);
            		//headtailslicedata2[imp.getSlice()-1][1]=clickedxyint;//tail
            	}
            	else
            	{
            		changeTargetpos(imp.getSlice()-1,2,clickedxyint);
            		//headtailslicedata2[imp.getSlice()-1][2]=clickedxyint;//mid
            	}
            	plotVel();
    		 */
    		//showoverlay(imp.getSlice()-1);


    		//closesed point moved to the location
    		/*
                int closerindex=closestPoint(clickedxyint, headtailslicedata2[imp.getSlice()-1]);
            	if(closerindex==0)//head
            	{
            		headtailslicedata2[imp.getSlice()-1][0]=new int[]{clickedxyint[0], clickedxyint[1]};            		
            	}
            	else if(closerindex==1)//tail
            	{
            		headtailslicedata2[imp.getSlice()-1][1]=new int[]{clickedxyint[0], clickedxyint[1]};            		            		
            	}
            	else //2 must be mid
            	{
            		headtailslicedata2[imp.getSlice()-1][2]=new int[]{clickedxyint[0], clickedxyint[1]};            		            		
            	}
            	showoverlay(imp.getSlice()-1);
    		 */

    		/*
            	//old way with felet
                int closerindex=closestPoint(clickedxy, headtailslicedata[imp.getSlice()]);
                if(closerindex!=0)//head should be 0
                {
                    IJ.log("need change direction");
                    changeHTdirecion(imp.getSlice());
                }
                //redraw
                if(trackedroi[imp.getSlice()]!=null)
                {
                    IJ.log("trackedroi not null");
                    Overlay overlay= new Overlay();
                    trackedroi[imp.getSlice()].setStrokeColor(Color.orange);
                    overlay.add(trackedroi[imp.getSlice()]);
                    if(headtailslicedata[imp.getSlice()]!=null)
                    {
                        IJ.log("headtailslicedata not null");

                        OvalRoi headcircleroi= new OvalRoi((int)headtailslicedata[imp.getSlice()][0][0]-2,(int)headtailslicedata[imp.getSlice()][0][1]-2, 5,5);
                        headcircleroi.setStrokeColor(Color.red);
                        overlay.add(headcircleroi);
                    }
                    //imp_work.setOverlay(overlay);
                    imp.setOverlay(overlay);
                }
    		 */

    		//}

    		if(wmarray[frameindex]!=null)
    		{
    			/*if(key1flag)//u
            	{
            		//do something
            	}
    			 */
    			IJ.log("swap head tail "+String.valueOf(frameindex));
    			wmarray[frameindex].swapHT();  
    		}
    	}
    	else if(plotimp==WindowManager.getCurrentImage())
    	{
    		IJ.log("plot clicked at "+String.valueOf(plotcursorpoint.x)+
    				" " +String.valueOf(plotcursorpoint.y));
    		IJ.log(String.valueOf(plot.LEFT_MARGIN)+" "+String.valueOf(pw.plotWidth));
    		int clickedslice=(int)((double)(plotcursorpoint.x-plot.LEFT_MARGIN)/pw.plotWidth*slicenumber);
    		IJ.log(String.valueOf(clickedslice));
    		if(adequateflags[clickedslice])
    		{
    			int swapstartindex=0;
    			int swapendindex=0;
    			for(int i=0; i<adqplist.size(); i++)
    			{
    				Adequateperiod tempadqp=adqplist.get(i);
    				double[][] tempdata=tempadqp.getData();
    				int tempstartindex=(int)tempdata[0][0];
    				int tempendindex=(int)tempdata[0][tempdata[0].length-1];
    				if(tempstartindex<=clickedslice && clickedslice<tempendindex)
    				{
    					swapstartindex=tempstartindex;
    					swapendindex=tempendindex;
    				}
    			}

    			//head tail track check
    			//int periodduration=tempdata[0].length;
    			int periodduration=swapendindex-swapstartindex+1;
    			//-1 because Adequateperiod started when both i-1 and i are ok.
    			for(int j=-1; j<periodduration; j++)
    			{
    				wmarray[swapstartindex+j-1].swapHT();
    				anglesarray[swapstartindex+j-1]=wmarray[swapstartindex+j-1].calcAngles();
    			}
    		}
    	}
    	velocity3=calVelocity3();
    	ImageProcessor plotip = plotVectordata();
    	plot.drawLine((double)imp.getSlice(), plottedmin, (double)imp.getSlice(),plottedmax );
    	plotip=plot.getProcessor();
    	plotimp.setProcessor(null,plotip);

    	wmarray[frameindex].showShape(imp, adequateflags[frameindex]);
        //iw.addKeyListener(this);
    }
    
    public void mouseEntered(MouseEvent e) {}
    public void mouseExited(MouseEvent e) {}
    public void mousePressed(MouseEvent e) {}
    public void mouseReleased(MouseEvent e) {}
    
    //when clicked, or typed undo key, change pos of feature (head tail mid)
    //and show overlay.
    void changeTargetpos(int frameindex, int feature, int[] xypos)//index start0, head tail mid, xy
    {
    	undodata= new int[]{frameindex,feature,headtailslicedata2[frameindex][feature][0],headtailslicedata2[frameindex][feature][1]};
		headtailslicedata2[frameindex][feature]=xypos;
		showoverlay(frameindex);
		velocity2=calVelocity2();
		calBend(frameindex);
    }
    
    //this could be used form other thread?
    //return closest index of targets array. ori and target shoul have 2 values
    //-1 is abnormal return val
    int closestPoint(double[] ori, double[][] targets)
    {
         //IJ.log("ori "+String.valueOf(ori[0])+" "+String.valueOf(ori[1]));
        double[] distancearray=new double[targets.length];
        int closestindex= -1;
        double mindistance=0;
        for(int i=0; i<targets.length;i++)
        {
            //IJ.log("target "+String.valueOf(i)+" " +String.valueOf(targets[i][0])+" "+String.valueOf(targets[i][1]));
            distancearray[i]=Math.sqrt(Math.pow(ori[0]-targets[i][0],2)+Math.pow(ori[1]-targets[i][1],2));
            if(i==0)
            {
                mindistance=distancearray[i];
                closestindex=i;
            }
            else if(mindistance>distancearray[i])
            {
                mindistance=distancearray[i];
                closestindex=i;
            }
        }
         return closestindex;
    }
    
    int closestPoint(int[] ori, int[][] targets)
    {
    	double[] oridouble=new double[]{(double)ori[0],(double)ori[1]};
    	double[][] targetsdouble=new double[targets.length][2];
    	for(int i=0;i<targetsdouble.length;i++)
    	{
    		targetsdouble[i]=new double[] {(double)targets[i][0],(double)targets[i][1]};
    	}
    	return closestPoint(oridouble, targetsdouble);
    }
    
    //invert direction for all after index slce
    void changeHTdirecion(int index)
    {
        if(headtailslicedata!=null)
        {
            for(int i=index; i<headtailslicedata.length; i++)
            {
                if(headtailslicedata[i]!=null)
                {
                    double[][] newdata=new double[][] {{headtailslicedata[i][1][0],headtailslicedata[i][1][1]},{headtailslicedata[i][0][0],headtailslicedata[i][0][1]}};
                    //IJ.log("slice "+String.valueOf(i)+" new h "+String.valueOf(newdata[0][0])+" "+String.valueOf(newdata[0][1])+" t "+String.valueOf(newdata[0][1])+" "+String.valueOf(newdata[0][1]));
                    headtailslicedata[i]=newdata;
                }
            }
            
        }
    
    }
    
    
    //calculate bending angle at middle.
    void calBend(int n)
    {
        //double[] returnvalue=new double[slicenumber];
    	//for(int i=0;i<slicenumber;i++)
        //{
        	int tailtomidx=headtailslicedata2[n][2][0]-headtailslicedata2[n][1][0];
        	int tailtomidy=headtailslicedata2[n][2][1]-headtailslicedata2[n][1][1];
        	int midtoheadx=headtailslicedata2[n][0][0]-headtailslicedata2[n][2][0];
        	int midtoheady=headtailslicedata2[n][0][1]-headtailslicedata2[n][2][1];
        	//double benddeg=MathEx.getAngleDiff(tailtomidx,tailtomidy,midtoheadx,midtoheady);
        	//returnvalue[i]=benddeg;
        //}
    	//return MathEx.getAngleDiff(tailtomidx,tailtomidy,midtoheadx,midtoheady);
        bendvec[n]= MathEx.getAngleDiff(tailtomidx,tailtomidy,midtoheadx,midtoheady);
    }
    
    //calculate velocity using stage pos, mid pos
    double[] calVelocity2()
    {
        double[] returnvalue=new double[slicenumber];
        range=new double[]{0,0};
        xvector=new double[slicenumber];
        xvector[0]=1;//strat from 1
        //mid position of 1st slice
        globalpos[0][0]=-stagepos[0][0]+headtailslicedata2[0][2][0]*scale;
        globalpos[0][1]=-stagepos[0][1]+headtailslicedata2[0][2][1]*scale;
        //after 2nd slice, calculate difference and angle etc
        for(int i= 1; i<slicenumber;i++)
        {
        	xvector[i]=i+1;
        	//locomotion distance
        	//stage coordinate is differ from image coordinate. inverted?
            globalpos[i][0]=-stagepos[i][0]+headtailslicedata2[i][2][0]*scale;
            globalpos[i][1]=-stagepos[i][1]+headtailslicedata2[i][2][1]*scale;
            double[] motionvec= 
            	new double[]{globalpos[i][0]-globalpos[i-1][0],globalpos[i][1]-globalpos[i-1][1]};
            double  distance=
            	Math.sqrt(Math.pow(motionvec[0],2)+Math.pow(motionvec[1],2));
            
            //tail to head vector
            double[] thvec=
            	new double[]{(double)(headtailslicedata2[i][0][0]-headtailslicedata2[i][1][0]),
            		(double)(headtailslicedata2[i][0][1]-headtailslicedata2[i][1][1])};
            //calc. angle between motionvec and heat to tailvec
            double angle=MathEx.getAngleDiff(motionvec[0],motionvec[1],thvec[0],thvec[1]);
            double sign=1;
            if(Math.abs(angle)>Math.PI/2)
            {
            	sign=-1;
            }
            returnvalue[i]=distance*sign;
            if(range[0]>returnvalue[i])
            {
            	range[0]=returnvalue[i];
            }
            if(range[1]<returnvalue[i])
            {
            	range[1]=returnvalue[i];
            }
        }
		//bending scaled with velocity range
		bendvecscaled=new double[slicenumber];
		for(int i=0; i<slicenumber;i++)
		{
			bendvecscaled[i]=bendvec[i]*range[0]/3;
		}
        
        return returnvalue;
    }
    
    //calculate velocity using stage pos, middle of joint of worm model
    double[] calVelocity3()
    {
    	double[] returnvalue = new double[slicenumber];
    	
    	for(int i=1; i<slicenumber; i++)
    	{
    		if(wmarray[i]!=null && wmarray[i-1]!=null)
    		{
    			//to reduce noise calc velocity at 7 points  
    			//and return the mean as velocity
    			double[] velocities=new double[7];
    			for(int j=0; j<velocities.length;j++)
    			{
    				int targetpoint=samplewm.joint.length/2-velocities.length/2+j;
    				double[] premiddlepoint= new double[] {
    						wmarray[i-1].joint[targetpoint][0]*scale-stagepos[i-1][0],
    						wmarray[i-1].joint[targetpoint][1]*scale-stagepos[i-1][1]};
    				double[] middlepoint=new double[] {
    						wmarray[i].joint[targetpoint][0]*scale-stagepos[i][0],
    						wmarray[i].joint[targetpoint][1]*scale-stagepos[i][1]};
    				double[] travelvector = new double[] {
    						(middlepoint[0]-premiddlepoint[0]),
    						(middlepoint[1]-premiddlepoint[1])};
    				int[] apos=wmarray[i].joint[targetpoint-1];
    				int[] ppos=wmarray[i].joint[targetpoint+1];
    				double[] directionvector = new double[] {(apos[0]-ppos[0])*scale,(apos[1]-ppos[1])*scale};
    				double dirveclength=Math.sqrt(Math.pow(directionvector[0],2)+
    						Math.pow(directionvector[1],2));
    				velocities[j] = MathEx.getInner2D(
    						(double)directionvector[0]/dirveclength,
    						(double)directionvector[1]/dirveclength,
    						(double)travelvector[0],
    						(double)travelvector[1]
    				);
    			}
    			returnvalue[i]=MathEx.mean(velocities);
    		}
    	}
    	return returnvalue;
    }
    
    //filter out abnormal worm model
    // using bodylength, joint angles.
    //also check head and tail detection
    // this method should be set parameters (% length, angle limit etc.)
    void filteringWm()
    {
    	double lengththreshold=0.15;//15%
    	double widththreshold=0.3;//30%?
    	double anglethreshold=Math.PI/2;//60 degree. too stringent? 90 degree
    	int jointnum=1;
    	samplewm=getasamplewm();
    	if(samplewm!=null)
    	{
    		jointnum=samplewm.joint.length;
    	}
    	else
    	{
    		IJ.log("no worm");
    		return;
    	}
    	//retrieve length, angle, width data from wormmodels
    	lengtharray=new double[slicenumber];
       	anglesarray=new double[slicenumber][jointnum];
    	sumwidtharray=new double[slicenumber];
       	adequateflags=new boolean[slicenumber];
       	int inadequatecount=0;
       	for(int i=0;i<slicenumber;i++)
       	{
       		Wormmodel tempwm=wmarray[i];
       		if(tempwm!=null)
       		{
       			adequateflags[i]=true;
       			anglesarray[i]=tempwm.calcAngles();
           		//angle filter
           		double[] trimmedangle=new double[anglesarray[0].length-6];
           		System.arraycopy(anglesarray[i], 3, trimmedangle, 0, anglesarray[0].length-6);
           		if(MathEx.max(trimmedangle)[0] > anglethreshold
           		|| MathEx.min(trimmedangle)[0] < -anglethreshold)
           		{
           			adequateflags[i]=false;
           			inadequatecount++;
           		}
       			lengtharray[i]=tempwm.getJoints().getLength();
       			sumwidtharray[i]=MathEx.sum(tempwm.widtharray);
       		}
       		else
       		{
       			inadequatecount++;       			
       		}
       	}
       	//these mean, max, min couldn't handle NA.... need fix later
       	//or for now use median
       	medianlength=MathEx.median(lengtharray);
       	double mediansumwidth=MathEx.median(sumwidtharray);
       	
       	//filtering with length and angles
       	//also prepare mean length, width and sharpnesso of head tail for later use
       	int adqcount=0;
       	double mlength = 0;
       	double[] mwidth=new double[samplewm.widtharray.length];
       	double[] msharpness=new double[2];//head tail
       	
       	for(int i=0;i<slicenumber;i++)
       	{
       		if(wmarray[i]!=null)
       		{
       			//lengthfilter
           		if(lengtharray[i]>medianlength+medianlength*lengththreshold
           		|| lengtharray[i]<medianlength-medianlength*lengththreshold)
           		{
           			adequateflags[i]=false;
           		}
           		else if(sumwidtharray[i]>mediansumwidth+mediansumwidth*widththreshold
           			|| sumwidtharray[i]<mediansumwidth-mediansumwidth*widththreshold)
           		{
           			adequateflags[i]=false;
           		}
           		if(adequateflags[i])
           		{
           			adqcount++;
           			mlength=mlength+lengtharray[i];
           			for(int j=0;j<mwidth.length;j++)
           			{
           				mwidth[j]=mwidth[j]+wmarray[i].widtharray[j];
           			}
           			msharpness[0]=msharpness[0]+sharpnessofheadandtail[i][0];
           			msharpness[1]=msharpness[1]+sharpnessofheadandtail[i][1];
           		}
       		}
       	}
       	mlength=mlength/adqcount;
		for(int j=0;j<mwidth.length;j++)
   		{
   			mwidth[j]=mwidth[j]/adqcount;
   		}
       	msharpness[0]=msharpness[0]/adqcount;
       	msharpness[1]=msharpness[1]/adqcount;
       	velocity3=calVelocity3();
       	
       	//Adequateperiod started at both i-1 and i are ok. so for velocity
        adqplist=new ArrayList<Adequateperiod>();
        boolean aperiodflag=false;
       	//Coiledperiod started at i is not adq.
        coiledplist=new ArrayList<Coiledperiod>();
        boolean cperiodflag=false;
        for(int i=1;i<slicenumber;i++)
        {
        	//IJ.log(String.valueOf(adequateflags[i]));
        	//at beginning of period
        	if(adequateflags[i-1]&&adequateflags[i]&&!aperiodflag)
        	{
        		aperiodflag=true;
        		Adequateperiod adqp=new Adequateperiod(i+1);//1 start
        		adqplist.add(adqp);
                //adequatevel.add(velocity3[i]);
        	}
        	else if(adequateflags[i-1]&&adequateflags[i]&&aperiodflag)
        	{
        		Adequateperiod tempadqp=adqplist.get(adqplist.size()-1);
        		tempadqp.addData(i+1);
        	}
        	else
        	{
        		aperiodflag=false;
        	}
        	
        	//coiled period
        	if(!adequateflags[i]&&!cperiodflag)
        	{
        		cperiodflag=true;
        		Coiledperiod coilp= new Coiledperiod(i+1);//1 start
        		coiledplist.add(coilp);
        	}
        	else if(!adequateflags[i]&&cperiodflag)
        	{
        		Coiledperiod tempcoilp= coiledplist.get(coiledplist.size()-1);
        		tempcoilp.addData(i+1);
        	}
        	else
        	{
        		cperiodflag=false;
        	}
        }
        
        //checking head tail detection
        //also get range
        double[] plotrange=new double[]{0,0};
        for(int i=0; i<adqplist.size(); i++)
        {
    		Adequateperiod tempadqp=adqplist.get(i);
    		double[][] tempdata=tempadqp.getData();
    		int startindex=(int)tempdata[0][0];
    		double[] temprange=MathEx.range(tempdata[1]);
    		if(temprange[0]<plotrange[0])
    			plotrange[0]=temprange[0];
    		if(temprange[1]>plotrange[1])
    			plotrange[1]=temprange[1];
    		
        	//head tail track check
    		int periodduration=tempdata[0].length;
    		int flipcount=0;
    		for(int j=0; j<periodduration; j++)
    		{
    			if(!checkDirection(startindex+j))
    			{
                  	wmarray[startindex+j-1].swapHT();          
                  	anglesarray[startindex+j-1]=wmarray[startindex+j-1].calcAngles();
    				flipcount++;
    			}
    		}
    		if(flipcount>periodduration/2)
    		{
    			IJ.log("flip all "+ String.valueOf(i));
        		//-1 because Adequateperiod started when both i-1 and i are ok.
        		for(int j=-1; j<periodduration; j++)
        		{
                  	wmarray[startindex+j-1].swapHT();
                  	anglesarray[startindex+j-1]=wmarray[startindex+j-1].calcAngles();
        		}
    		}
       	}
       	velocity3=calVelocity3();
       	
       	
       	//fitting/speculating coiled posture...
       	
       	if(coiledplist.size()>0)
       	{
       		for(int i=0; i<coiledplist.size();i++)
       		{
       			Coiledperiod tempcoiledp=coiledplist.get(i);
       			double[][] tempdata=tempcoiledp.getData();
       			int startindex=(int)tempdata[0][0];
       			
       			for(int j=0; j<tempdata[0].length;j++)
       			{
       				IJ.log(String.valueOf(startindex+j));
       				imp.setSlice(startindex-1+j);
       				ImageProcessor preip=imp.getProcessor().duplicate();
       				imp.setSlice(startindex+j);
       				ImageProcessor curip=imp.getProcessor().duplicate();


           			//OpticalFlow(ImagePlus imp1_, ImagePlus imp2_, 
            		//		int x_, int y_, int blocksize_, int range_)            		                                      
            		OpticalFlow of=new OpticalFlow(curip, preip);
       				//int[] searchfit(int x_, int y_, int blocksize_, int range_)

       				int[][] prejoint=wmarray[startindex-2+j].joint;
       				//IJ.log("prejoint x "+String.valueOf(prejoint[0][0])+
       				//		" y "+String.valueOf(prejoint[0][1]));
       				int[][] speculatedjoint=new int[samplewm.joint.length][2];
       				Overlay overlay= new Overlay();

       				for(int k=0;k<prejoint.length;k++)
       				{
       					int testposx=prejoint[k][0];
       					int testposy=prejoint[k][1];
       					//int[] dirvec=of.searchfit(testposx-5,testposy-5,15,10);
       					int[] dirvec=of.searchfit(testposx-5,testposy-5,10,10);
       					speculatedjoint[k][0]=testposx+dirvec[0];
       					speculatedjoint[k][1]=testposy+dirvec[1];
       					//IJ.log("dirvec "+String.valueOf(dirvec[0])
       					//		+" "+String.valueOf(dirvec[1]));
       					OvalRoi pointcirc= new OvalRoi(testposx-2,
       							testposy-2, 5,5);
       					pointcirc.setStrokeColor(Color.green);
       					overlay.add(pointcirc);
       					PolygonRoi templine=new PolygonRoi
       					(new int[]{testposx,testposx+dirvec[0]},
       							new int[]{testposy,testposy+dirvec[1]},
       							2,Roi.POLYLINE);
       					templine.setStrokeColor(Color.green);
       					overlay.add(templine);      
       				}

       		        ImageProcessor ipfilterd=curip.duplicate();
       		        RankFilters rf = new RankFilters();
       		        rf.rank(ipfilterd, 5, 4);// radius 5, median filter (4)
       				ArrayList<Roi> roiarraylist=rt.scanImage(ipfilterd);

       				
       		        //if(startindex+j==90)
       		        //{
       		        	//roiarraylist= rt.scanImage(curip,true);
       		        	//ImageProcessor dupcurip=curip.duplicate();
       		        	//ImagePlus imp_check=new ImagePlus("curip", dupcurip);
       		        	//imp_check.show();
       		        	//return;
       		        //}
       		        //ArrayList<Roi> scanImage(ImageProcessor ip_)
       				//ArrayList<Roi> roiarraylist= rt.scanImage(curip);
       				//for now assume just find one roi
       	        	java.awt.Polygon pol=roiarraylist.get(0).getPolygon();
       	        	OutlinedShape os=
       	        		new OutlinedShape(pol.xpoints, pol.ypoints, pol.npoints, imp);
            		boolean arethereend=os.detectEnds();
            		//if seems succeed detection by outlinshape, true. 0 head and 1 tail
            		boolean[] betterheadandtaildetection=new boolean[2];
            		/*
       				if(osarray[startindex+j-2]==null)
       				{
           		        ImageProcessor preipfilterd=preip.duplicate();
           		        //RankFilters prerf = new RankFilters();
           		        rf.rank(preipfilterd, 5, 4);// radius 5, median filter (4)
           				ArrayList<Roi> preroiarraylist=rt.scanImage(preipfilterd);
           	        	java.awt.Polygon prepol=preroiarraylist.get(0).getPolygon();
           	        	OutlinedShape preos=new OutlinedShape(prepol.xpoints, 
           	        			prepol.ypoints, prepol.npoints, 
           	        			new ImagePlus("",preipfilterd));
           	        	osarray[startindex+j-2]=preos;
       				}*/	
       				if(os.detectedends==0)
            		{
            			//nothing to do
            			IJ.log("no possible end");
            		}
            		else 
            		{
            			for(int k=0;k<os.otrarraylist.size();k++)
            			{
            				//os.otrarraylist.get(i)
            				
            				
            			}
                		//if there is end, three possiblity
                		//head, tail or not both
        				double diffofsharpenttohead=
        					Math.sqrt(
        							Math.pow(os.sharpendmaxanglediff-msharpness[0],2));
        				double diffofsharpenttotail=
        					Math.sqrt(
        							Math.pow(os.sharpendmaxanglediff-msharpness[1],2));
        				//seems tail
        				if(diffofsharpenttohead>diffofsharpenttotail)
        				{
                			IJ.log("tail");
        					speculatedjoint[speculatedjoint.length-1][0]
        					 =os.sharpendxy[0];
        					speculatedjoint[speculatedjoint.length-1][1]
        					 =os.sharpendxy[1];
        					betterheadandtaildetection[1]=true;
        				}
        				else if(diffofsharpenttohead<diffofsharpenttotail)//head?
        				{
        					//this threashold must be adjusted later
        					//too round so its not head
        					if(diffofsharpenttohead>msharpness[1]-msharpness[0])
        					{
                    			IJ.log("too round");
        					}
        					else
        					{
                    			IJ.log("head");
            					speculatedjoint[0][0]
            					=os.sharpendxy[0];
            					speculatedjoint[0][1]
            					=os.sharpendxy[1];
            					betterheadandtaildetection[0]=true;
        					}
        				}
        				
        				//if there are 2 possible ends, sharp one was processed above,
        				//so just consider head or other
            			if(os.detectedends==2)
            			{
            				double diffofroundenttohead=
            					Math.sqrt(
            					Math.pow(os.roundendmaxanglediff-msharpness[0],2));
        					if(diffofroundenttohead>msharpness[1]-msharpness[0])
        					{
                    			IJ.log("tail ok but another is too round");
        					}
        					else
        					{
                    			IJ.log("tail and head");
            					speculatedjoint[0][0]
            		            =os.roundendxy[0];
            		            speculatedjoint[0][1]
            		            =os.roundendxy[1];
            					betterheadandtaildetection[0]=true;
        					}
            			}
            		}
            		IJ.log(String.valueOf(betterheadandtaildetection[0])
            				+" "+String.valueOf(betterheadandtaildetection[1]));
            		
            		//check if each speculated points are not significantly differ
            		//innter is x, outer is y normalized axis vec as reference
            		double[] innerofaxisandmotion=new double[samplewm.joint.length];
            		double[] outerofaxisandmotion=new double[samplewm.joint.length];
            		
            		for(int k=0;k<samplewm.joint.length;k++)
            		{
            			double[] axisvec=new double[2];
            			double[] rawaxisvec=new double[2];
            			if(k<samplewm.joint.length-1)
            			{
            				rawaxisvec=new double[]{
            					prejoint[k][0]-prejoint[k+1][0], 
            					prejoint[k][1]-prejoint[k+1][1]};
            			}
            			else
            			{
                			rawaxisvec=new double[]{
                					prejoint[k-1][0]-prejoint[k][0], 
                					prejoint[k-1][1]-prejoint[k][1]};
            			}
        				double axislength=Math.sqrt(Math.pow(rawaxisvec[0],2)+
        						Math.pow(rawaxisvec[1], 2));
        				axisvec=new double[]{rawaxisvec[0]/axislength,
        						rawaxisvec[1]/axislength};
            			innerofaxisandmotion[k]=
            				MathEx.getInner2D(axisvec[0], axisvec[1], 
            					speculatedjoint[k][0]-prejoint[k][0],
            					speculatedjoint[k][1]-prejoint[k][1]);
            			outerofaxisandmotion[k]=
            				MathEx.getOuter2D(axisvec[0], axisvec[1], 
            					speculatedjoint[k][0]-prejoint[k][0],
            					speculatedjoint[k][1]-prejoint[k][1]);
            		}
            		
            		double[] mediandiffvector=new double[]{
            		MathEx.median(innerofaxisandmotion),
            		MathEx.median(outerofaxisandmotion)};
            		//IJ.log("mediandiffvec "+String.valueOf(mediandiffvector[0])+" "+
            		//		String.valueOf(mediandiffvector[1]));
            		ArrayList<int[]> speculatedpointsarray=new ArrayList<int[]>();
            		for(int k=0;k<innerofaxisandmotion.length;k++)
            		{
            			//if head is not detected, tail is not detected,
            			// and not head nor tail
            			if((k==0 && !betterheadandtaildetection[0]) ||
            				(k==innerofaxisandmotion.length-1 && !betterheadandtaildetection[1])||
            				(k>0 && k<innerofaxisandmotion.length-1) )
            			{
            				double innerwithmedianvec=
            					MathEx.getInner2D(
            							innerofaxisandmotion[k],
            							outerofaxisandmotion[k],
            							mediandiffvector[0],
            							mediandiffvector[1]);
            				if(innerwithmedianvec<=0)//for now check if opposit direction
            				{
            					//IJ.log("need to fix "+String.valueOf(k));
            					double[] axisvec=new double[2];
            					double[] rawaxisvec=new double[2];
            					if(k<samplewm.joint.length-1)
            					{
            						rawaxisvec=new double[]{
            								prejoint[k][0]-prejoint[k+1][0], 
            								prejoint[k][1]-prejoint[k+1][1]};
            					}
            					else
            					{
            						rawaxisvec=new double[]{
            								prejoint[k-1][0]-prejoint[k][0], 
            								prejoint[k-1][1]-prejoint[k][1]};
            					}
            					double axisradian=Math.atan2(rawaxisvec[1], rawaxisvec[0]);
            					double[] newposvec=
            						MathEx.rotVec(mediandiffvector, axisradian);
            					speculatedjoint[k][0]=prejoint[k][0]+(int)newposvec[0];
            					speculatedjoint[k][1]=prejoint[k][1]+(int)newposvec[1];
            				}

            				//if its not on worm, eliminate. except for head tail
            				if((k>0 && k<innerofaxisandmotion.length-1)&&
            					(ipfilterd.getPixel(speculatedjoint[k][0], 
            						speculatedjoint[k][1])> fieldvaluearray[2]))
            				{
            					
            					IJ.log("not on worm "+String.valueOf(k));
            				}
            				else
            				{
            					speculatedpointsarray.add(speculatedjoint[k]);
            				}
            			}
            			else
            			{
        					speculatedpointsarray.add(speculatedjoint[k]);

            			}
            		}
            		
            		imp.setProcessor(ipfilterd);
            		//Wormmodel speculatedmodel=
       				//	new Wormmodel(speculatedjoint, mwidth, mlength, imp, fieldvaluearray[2]);
            		Wormmodel speculatedmodel=
       					new Wormmodel(speculatedpointsarray, mwidth, mlength, 
       							imp, fieldvaluearray[2],betterheadandtaildetection, os);
            		//os=null;
            		imp.setProcessor(curip);
       				wmarray[startindex+j-1]=speculatedmodel;
       				osarray[startindex+j-1]=os;
       				osarray[startindex+j-2]=null;
       				//if(startindex+j==90)return;
       				//if(true)return;
       			}
       			//imp.setOverlay(overlay); 
       			//return;
       		}
       	}
       	
       	
       	
       	//xvector for ploting x axis
        xvector=new double[slicenumber];
        xvector[0]=1;//strat from 1
        for(int i=0; i<slicenumber;i++)
        {
        	xvector[i]=i+1;
        }
        plot=new Plot("plot","slicenum","velocity",
        		new double[]{0.0},new double[]{0.0});
        //double[] plotrange=MathEx.range(velocity3);
        ImageProcessor protip = 
        	plotVectordata(plot,shorttitle, plotrange[0], plotrange[1]);
        pw=plot.show();
        pw.close();
        plotimp=new ImagePlus(shorttitle,protip);
        plotimp.show();
        
        plotiw = plotimp.getWindow();
        plotic= plotiw.getCanvas();
        plotic.addMouseListener(this);
       	
    }
    
    //calculate sum of distance of each joint as normal and inverse way
    //and if it inverse, ruturn false
    boolean checkDirection(int index)
    {
    	int[][] prejoint=wmarray[index-1-1].joint;
    	int[][] curjoint=wmarray[index-1].joint;
    	double norm=0;
    	double inv=0;
    	for(int i=0;i<curjoint.length;i++)
    	{
    		norm=norm+Math.sqrt(Math.pow(prejoint[i][0]-curjoint[i][0],2)+
    				Math.pow(prejoint[i][1]-curjoint[i][1],2));
    		inv=inv+Math.sqrt(Math.pow(prejoint[i][0]-curjoint[curjoint.length-1-i][0],2)+
    	    		Math.pow(prejoint[i][1]-curjoint[curjoint.length-1-i][1],2));
    	}
    	if(norm<inv)
    		return true;
    	else
    		return false;
    	
    }
    
    class Adequateperiod
    {
    	ArrayList<Integer> indices;
    	ArrayList<Double> velocity;
    	Adequateperiod(int startindex)
    	{
    		indices=new ArrayList<Integer>();
    		velocity=new ArrayList<Double>();
    		indices.add(startindex);
    		velocity.add(velocity3[startindex]);
    	}
    	
    	void addData(int i)
    	{
    		indices.add(i);
    		//this doesn't need?
    		velocity.add(velocity3[i-1]);
    	}
    	
    	double[][] getData()
    	{
    		double[][] returndata=new double[3][indices.size()];
    		for(int i=0;i<indices.size();i++)
    		{
    			returndata[0][i]=(double)indices.get(i);
    			//returndata[1][i]=velocity.get(i);
    			returndata[1][i]=velocity3[indices.get(i)-1];
    			returndata[2][i]=lengtharray[indices.get(i)-1];
    		}
    		return returndata;
    	}
    }
    
    
    class Coiledperiod
    {
    	ArrayList<Integer> indices;
    	ArrayList<Double> velocity;
    	Coiledperiod(int startindex)
    	{
    		indices=new ArrayList<Integer>();
    		velocity=new ArrayList<Double>();
    		indices.add(startindex);
    		velocity.add(velocity3[startindex]);
    	}
    	
    	void addData(int i)
    	{
    		indices.add(i);
    		//this doesn't need?
    		velocity.add(velocity3[i-1]);
    	}
    	
    	double[][] getData()
    	{
    		double[][] returndata=new double[3][indices.size()];
    		for(int i=0;i<indices.size();i++)
    		{
    			returndata[0][i]=(double)indices.get(i);
    			//returndata[1][i]=velocity.get(i);
    			returndata[1][i]=velocity3[indices.get(i)-1];
    			returndata[2][i]=lengtharray[indices.get(i)-1];
    		}
    		return returndata;
    	}    	
    }
    
    Wormmodel getasamplewm()
    {
    	for(int i=0;i<wmarray.length;i++)
    	{
    		if(wmarray[i]!=null)
    			return wmarray[i];
    	}
    	return null;
    }
    
    
    ImageProcessor plotVectordata(Plot plot_, String shorttitle, double min, double max)
    {
    	//plot velocity3 and length?
    	//these are reused at slice indicater
    	plottedmin=min;
    	plottedmax=max;

        plot_.setLimits(0,slicenumber,min,max);
 
        //IJ.log(String.valueOf(adqplist.size()));
        //loop size of the list addpoints
        for(int i=0; i<adqplist.size(); i++)
        {
    		Adequateperiod tempadqp=adqplist.get(i);
    		double[][] tempdata=tempadqp.getData();
            plot_.setColor(Color.black);
            plot_.addPoints(tempdata[0],tempdata[1],2);        	
    		double[] modifiedlength=new double[tempdata[0].length];
    		for(int j=0; j<modifiedlength.length;j++)
    		{
    			modifiedlength[j]=tempdata[2][j]-medianlength;
    		}
            plot_.setColor(Color.red);
            plot_.addPoints(tempdata[0],modifiedlength,2);        	
        }

        plot_.setColor(Color.black);
        plot_.addLabel(0,1,shorttitle);    	
    	//pw=plot_.show();
    	return plot_.getProcessor();
    }
    
    ImageProcessor plotVectordata()
    {
    	return plotVectordata(plot,shorttitle, plottedmin, plottedmax);
    }
    
    
    
    //plotter of velocity
    void plotVel()
    {
		//plot.setLimits(1,slicenumber,range[0],range[1]);
    	//plotmin=range[0];
    	plotmin=-range[1];
    	plotmax=range[1];
		plot.setLimits(1,slicenumber,plotmin,plotmax);
		plot.setColor(Color.gray);
		plot.drawLine(imp.getSlice(),plotmin,imp.getSlice(),plotmax);
		//start from 2nd slce since speed need two point and cant cali at 1st
		/*
    	for(int i=1;i<slicenumber;i++)
    	{
            plot.addPoints(new double[]{(double)i}, 
            		new double[]{velocity2[i]}, 2);//circle 0, dot 6. line is 2
    	}*/
		//IJ.log("length "+String.valueOf(velocity2.length));
		plot.setColor(Color.black);
		plot.addPoints(xvector, velocity2, 2);
		
		plot.setColor(Color.red);
		plot.addPoints(xvector, bendvecscaled, 2);
        pw.drawPlot(plot);
    }
    
    //calculate velocity using head tail pos and periods data
    double[] calVelocity()
    {
        double[] returnvalue=new double[slicenumber];
        for(int k=0; k<periodsdat.length/2;k++)
        {
            int startindex=periodsdat[k*2];
            int endindex=periodsdat[k*2+1];
            for(int j=startindex+1; j<=endindex;j++)//to calculate speed start from 2nd slice
            {
                //periodsdat 's index start from 0
                //headtailslicedata 's index is equal to imp slice (start from1 not 0) this is not good. need fix later 
                //if(headtailslicedata[j+1]!=null)
                if(roidetected[j-1]==true && roidetected[j]==true)//both this and previous slice have data
                {
                    //IJ.log("headtailslicedata[j+1] not null "+String.valueOf(j));
                    double[] speedandtheta=speedandTheta(j+1);
                    double tailheadangle=Math.atan2(headtailslicedata[j+1][0][1]-headtailslicedata[j+1][1][1], headtailslicedata[j+1][0][0]-headtailslicedata[j+1][1][0]);
                    //if anngle difference is larger than 90 degree -1, 
                    double sign=1;
                    if(Math.cos(speedandtheta[1]-tailheadangle)<0)
                    {
                        sign=-1;
                    }
                    returnvalue[j]=speedandtheta[0]*sign;
                }
            }
            
        }
        return returnvalue;
    }
    
    //return how many pixels moved from previous slice and radian theta
    double[] speedandTheta(int index)
    {
        double premiddlex=(headtailslicedata[index-1][0][0]+headtailslicedata[index-1][1][0])/2.0;
        double premiddley=(headtailslicedata[index-1][0][1]+headtailslicedata[index-1][1][1])/2.0;
        double currentmiddlex=(headtailslicedata[index][0][0]+headtailslicedata[index][1][0])/2.0;
        double currentmiddley=(headtailslicedata[index][0][1]+headtailslicedata[index][1][1])/2.0;
        double speed=Math.sqrt(Math.pow(currentmiddlex-premiddlex,2)+Math.pow(currentmiddley-premiddley,2));
        double theta=Math.atan2(currentmiddley-premiddley,currentmiddlex-premiddlex);
        return new double[] {speed, theta};
    }
    
    //put NA fro velocity string
    String[] calVelString()
    {
    	//new new version
    	velocity=calVelocity();
    	String[] velocitystring=new String[slicenumber];
    	for(int i=0; i<slicenumber;i++)
    	{
    		velocitystring[i]=String.valueOf(velocity[i]);
    	}
	    //begining 
	    for(int i=0; i<=periodsdat[0];i++)
	    {
	    	velocitystring[i]="NA";
	    }
	    for(int k=0; k<periodsdat.length/2-1;k++)
	    {
	        int gapstartindex=periodsdat[k*2+1]+1;
	        int gapendindex=periodsdat[k*2+2];
	        for(int i=gapstartindex;i<=gapendindex;i++)
	        {
	        	velocitystring[i]="NA";
	        }
	    }
	    //end
	    for(int i=periodsdat[periodsdat.length-1]+1; i<slicenumber;i++)
	    {
	    	velocitystring[i]="NA";
	    }
	    //in the cas of there is no roi detected
	    for(int i=0; i<slicenumber;i++)
	    {
	    	if(roidetected[i+1]==false)
	    	{
	    		velocitystring[i]="NA";
	    		if(i!=slicenumber-1)
	    		{
	    			velocitystring[i+1]="NA";
	    		}
	    	}
	    }
	    return velocitystring;
	}

    String prepRoidataforOutput(int index)
    {
        StringBuffer strbuff=new StringBuffer();
        if(roidetected[index]==false)
        {
        	strbuff.append("NA,NA,NA,NA,NA,NA,NA,NA");
        }
        else
        {
		    //head and tail data
		    strbuff.append(String.valueOf(headtailslicedata[index][0][0])+",");
		    strbuff.append(String.valueOf(headtailslicedata[index][0][1])+",");
		    strbuff.append(String.valueOf(headtailslicedata[index][1][0])+",");
		    strbuff.append(String.valueOf(headtailslicedata[index][1][1])+",");
		    //centor of roi
		    Rectangle roibound=trackedroi[index].getBounds();
		    strbuff.append(String.valueOf(roibound.getCenterX())+",");
		    strbuff.append(String.valueOf(roibound.getCenterY())+",");
		    //feret max min
		    strbuff.append(String.valueOf(trackeddata[index][0])+",");//max
		    strbuff.append(String.valueOf(trackeddata[index][2]));//min
        }
	    return strbuff.toString();
    }
    
    //output velocity, bendangle, (these two are culculated data),
    //xyz pos of stage, xy of head, tail, mid. for checking on R
    void outPutdata()
    {
        //output as text
        String strforsave;
        String header="";
        String BR = System.getProperty("line.separator");
        
        //header preparation
        //1st line has rois width height
        /*
        for(int i=0; i<periodsdat.length;i++)
        {
            String periodsstr=String.valueOf(periodsdat[i]);
            
            header=header+periodsstr+",";
        }
        header=header+BR;
        */
        //2nd line 9 elements
        //header=header+"velocity, head_x, head_y, tail_x, tail_y, x, y, feret_max, feret_min";
        //header=header+"xpos, ypos, zpos, velocity, head_x, head_y, tail_x, tail_y, x, y, feret_max, feret_min";
        header="velocity, bendangle, " +
        		"xpos, ypos, zpos, head_x, head_y, tail_x, tail_y, mid_x, mid_y";
        //header=header+BR;
        strforsave=header+BR;
        StringBuffer strbuff=new StringBuffer();
        String aslicestring="";
        
        //new new version
        //String[] velocitystring=calVelString();
        
        for(int i=0;i<slicenumber;i++)
        {
        	strbuff.append(String.valueOf(velocity2[i])+","+String.valueOf(bendvec[i])+","
        			+String.valueOf(stagepos[i][0])+","+String.valueOf(stagepos[i][1])+","
        			+String.valueOf(stagepos[i][2])+","
        			+String.valueOf(headtailslicedata2[i][0][0])+","+String.valueOf(headtailslicedata2[i][0][1])+","
        			+String.valueOf(headtailslicedata2[i][1][0])+","+String.valueOf(headtailslicedata2[i][1][1])+","
        			+String.valueOf(headtailslicedata2[i][2][0])+","+String.valueOf(headtailslicedata2[i][2][1])
        			+BR);
        	//strbuff.append(velocitystring[i]+","+prepRoidataforOutput(i+1)+BR);
        }

        
        strforsave=strforsave+strbuff.toString();
        //SaveDialog(java.lang.String title, java.lang.String defaultDir, java.lang.String defaultName, java.lang.String extension)
        SaveDialog sd=new SaveDialog("save data", defdir, shorttitle, "");
        String chosendir=sd.getDirectory();
        String chosenfilename=sd.getFileName();
        if(chosenfilename==null)//canceled
        {
        	return;
        }
        IJ.saveString(strforsave,chosendir+chosenfilename+".txt");//save data into same dir name as imagetitle.txt
        IJ.log("Output is saved in; "+chosendir);    	
    }
    
    //velocity3, angles, xyz pos of stage, joints position, width array
    void outPutdata2()
    {
        //output as text
        String strforsave;
        String BR = System.getProperty("line.separator");
        String header="velocity,length";
        
        String anglelabel="";
        for(int i=1;i<=33;i++)
        {
        	anglelabel=anglelabel+
        	",angle_"+String.valueOf(i);
        }
        String poslabel=",xpos, ypos, zpos";
        //		"head_x, head_y, tail_x, tail_y, mid_x, mid_y";
        String jointlabel="";
        for(int i=1;i<=33;i++)
        {
        	jointlabel=jointlabel+
        	",joint_"+String.valueOf(i)+"_x"
        	+",joint_"+String.valueOf(i)+"_y";
        }
        String widthlabel="";
        for(int i=1;i<=33;i++)
        {
        	widthlabel=widthlabel+
        	",width_"+String.valueOf(i);
        }
        //header=header+BR;
        strforsave=header+anglelabel+poslabel+jointlabel+widthlabel+BR;
        StringBuffer strbuff=new StringBuffer();
        String aslicestring="";
        
        for(int i=0;i<slicenumber;i++)
        {
        	//velocity3
        	String velstr=String.valueOf(velocity3[i]);
        	if(i==0)
        		velstr="NA";
        	else if(!adequateflags[i-1]||!adequateflags[i])
        		velstr="NA";
        	strbuff.append(velstr+",");
        	//length
        	strbuff.append(String.valueOf(lengtharray[i])+",");        	
        	//anglesarray
        	if(wmarray[i]!=null)
        	{
        		for(int j=0;j<anglesarray[0].length;j++)
        		{
        			strbuff.append(
        					String.valueOf(anglesarray[i][j])+",");

        		}
        	}
        	else
        	{
                for(int j=1;j<=33;j++)
                {
        			strbuff.append(",");
                }        		
        		
        	}
        	//stage pos
        	strbuff.append(
        			String.valueOf(stagepos[i][0])+","
        			+String.valueOf(stagepos[i][1])+","
        			+String.valueOf(stagepos[i][2]));
        	//joint pos
        	if(wmarray[i]!=null)
        	{
        		PolygonRoi jointroi=wmarray[i].getJoints();
        		Polygon jointpoly=jointroi.getPolygon();
        		int[] xpoints=jointpoly.xpoints;
        		int[] ypoints=jointpoly.ypoints;
        		for(int j=0; j<jointpoly.npoints;j++)
        		{
        			strbuff.append(","
        					+String.valueOf(xpoints[j])+","
        					+String.valueOf(ypoints[j]));
        		}
        	}
        	else
        	{
                for(int j=1;j<=33;j++)
                {
        			strbuff.append(","+",");
                }        		
        	}
        	//width
        	if(wmarray[i]!=null)
        	{
        		for(int j=0; j<33;j++)
        		{
        			strbuff.append(","
        					+String.valueOf(wmarray[i].widtharray[j]));
        		}
        	}
        	else
        	{
                for(int j=1;j<=33;j++)
                {
        			strbuff.append(",");
                }        		
        	}
        	strbuff.append(BR);
        }

        
        strforsave=strforsave+strbuff.toString();
        //SaveDialog(java.lang.String title, java.lang.String defaultDir, java.lang.String defaultName, java.lang.String extension)
        SaveDialog sd=new SaveDialog("save data", defdir, shorttitle, "");
        String chosendir=sd.getDirectory();
        String chosenfilename=sd.getFileName();
        if(chosenfilename==null)//canceled
        {
        	return;
        }
        IJ.saveString(strforsave,chosendir+chosenfilename+".txt");//save data into same dir name as imagetitle.txt
        IJ.log("Output is saved in; "+chosendir);    	
    	
    }

    
    

}//public class BFtracker_ extends PlugInFrame  implements ActionListener,ImageListener,MouseListener{ end

























































class RoidatcorrectorThread extends Thread {
    
	BFtracker_ tpf;
    ImagePlus imp;
    ImageProcessor ip;//ip changes when change imp slice
    ImagePlus tempimp;//tempimp also changes when change slice
    ImageProcessor ip_work;
    ImagePlus imp_work;
    ImageCanvas ic;
    int[] fieldvaluearray;//min, max, threshold
    int width;
    int height;
    int slicenumber;
    int fillingvalue=255;
    int[] periodsdat;// having stable slice start and end indexes
    //double[][] trackeddata;//[slice index][mean, x, y, feret, angle, min, feretx, ferety]
    double[] theta;//angle of tail to head vector.
    double pretheta;
    
    RoidatcorrectorThread(BFtracker_ tpf)
    {
    	//IJ.log("constructor start");
        this.tpf=tpf;
        imp=tpf.imp;
        ip=tpf.ip;
        tempimp=new ImagePlus("temp", ip);//tempimp also changes when change slice
        ip_work=ip.duplicate();
        imp_work=new ImagePlus("work", ip_work);
        //imp_work.show();
        //ic=tpf.ic;
        width=tpf.width;
        height=tpf.height;
        slicenumber=tpf.slicenumber;
        if(tpf.bitdepth!=8)
        {
        	fillingvalue=65535;
        }
        fieldvaluearray=tpf.fieldvaluearray;
        //periodsdat=tpf.periodsdat;
        //trackeddata=tpf.trackeddata;
        tpf.trackeddata=new double[slicenumber+1][5];
        tpf.stagepos=new double[slicenumber][3];//x,y,z
        
        //tpf.trackedroi=new ArrayList<Roi>(slicenumber); 
        tpf.trackedroi=new Roi[slicenumber+1]; 
        theta=new double[slicenumber+1];
        tpf.headtailslicedata=new double[slicenumber+1][2][2];
        tpf.roidetected=new boolean[slicenumber+1];
        tpf.sharpnessofheadandtail=new double[slicenumber][2];
        tpf.headtailslicedata2=new int[slicenumber][3][2];//head tail middle
        tpf.osarray=new OutlinedShape[slicenumber];
        //IJ.log("trackeddata length"+String.valueOf(tpf.trackeddata.length));
        imp.setSlice(1);//cant change slice?
        //IJ.log("imp? "+String.valueOf(imp.getSlice()));
    }
    
    public void run()
    {
        IJ.log("start");
        this.startProcess("from thread");
    }
    
    //has bug. temparray.length/2+1; must be .length/2;
    double median(double[] vector)
    {
        double[] temparray=new double [vector.length];//To do deep copy, make new instance.
        for(int i=0; i<vector.length;i++)
        {
            temparray[i]=vector[i];
        }
        java.util.Arrays.sort(temparray);
        int middle=temparray.length/2+1;
        return temparray[middle];
    }
    
    //
    double[] runmed(double[] vector, int window)
    {
        double[] outputvec=new double [vector.length];//To do deep copy, make new instance.
        int middle=window/2+1;
        double[] temparray=new double [window];
        for(int i=0; i<vector.length-window ;i++)
        {
            System.arraycopy(vector, i, temparray,0,window);
            outputvec[i+middle]=median(temparray);
            
        }
        return outputvec;
    }
    
    //return closest index of targets array. ori and target shoul have 2 values
    //-1 is abnormal return val
    int closestPoint(double[] ori, double[][] targets)
    {
         //IJ.log("ori "+String.valueOf(ori[0])+" "+String.valueOf(ori[1]));
        double[] distancearray=new double[targets.length];
        int closestindex= -1;
        double mindistance=0;
        for(int i=0; i<targets.length;i++)
        {
            //IJ.log("target "+String.valueOf(i)+" " +String.valueOf(targets[i][0])+" "+String.valueOf(targets[i][1]));
            distancearray[i]=Math.sqrt(Math.pow(ori[0]-targets[i][0],2)+Math.pow(ori[1]-targets[i][1],2));
            if(i==0)
            {
                mindistance=distancearray[i];
                closestindex=i;
            }
            else if(mindistance>distancearray[i])
            {
                mindistance=distancearray[i];
                closestindex=i;
            }
        }
         return closestindex;
    }
    
    //return closest index of roi. ori and target shoul have 2 values
    //-1 is abnormal return val
    int closestRoi(double[] ori, ArrayList<Roi> targets)
    {
         //IJ.log("ori "+String.valueOf(ori[0])+" "+String.valueOf(ori[1]));
        double[] distancearray=new double[targets.size()];
        int closestindex= -1;
        double mindistance=0;
        for(int i=0; i<targets.size();i++)
        {
            //IJ.log("target "+String.valueOf(i)+" " +String.valueOf(targets[i][0])+" "+String.valueOf(targets[i][1]));
            //Roi temproi=targets.get(i);
            java.awt.Polygon pol=targets.get(i).getPolygon();
            //pol.xpoints, pol.ypoints
            int n=pol.npoints;
            double sumdist=0;
            for(int j=0;j<n;j++)
            {
            	sumdist=sumdist+
            	Math.sqrt(Math.pow(ori[0]-pol.xpoints[j],2)
            			+Math.pow(ori[1]-pol.ypoints[j],2));
            	
            }
            
            //distancearray[i]=
            //	Math.sqrt(Math.pow(ori[0]-targets[i][0],2)+Math.pow(ori[1]-targets[i][1],2));
            distancearray[i]=sumdist;
            if(i==0)
            {
                mindistance=distancearray[i];
                closestindex=i;
            }
            else if(mindistance>distancearray[i])
            {
                mindistance=distancearray[i];
                closestindex=i;
            }
        }
         return closestindex;
    }    
    //compare a and b radian, and if the difference is larger than pi/2 (90 degree) rotate b 180 degree
    //obsolate use Mathex.getAngleDiff or still need to change 180?
    double getSmallerAngle(double a, double b)
    {
        double returnval=b;
        if(Math.cos(b-a)<0)
        {
            returnval=Math.atan2(Math.sin(b-a)*-1, Math.cos(b-a)*-1)+a;
        }
        return returnval;
    }
    
    
    //compare angle and two points, and determine which is head and tail
    double[][] assignDirection(double[][] points, double theta)
    {
        //double[] relativehead=new double[] {Math.sin(theta), Math.con(theta)};
        double[][] returnarray= points;
        //1st point as origine
        double firstorigin= Math.atan2(points[1][1]-points[0][1],points[1][0]-points[0][0]);
        if(Math.cos(firstorigin-theta)>0)
        {
            returnarray=new double[][] {points[1],points[0]};
        }
        return returnarray;
    }
    
    /////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //extract xyz stage position data from meta data. taken from DVtracer
    public double[] getSlicepos()
    {
    //check if the image has xyzpos data in Fileinfo.info field as "xpos="+String.valueOf(xposarray[i])+",ypos="+String.valueOf(yposarray[i])+",zpos="+String.valueOf(zpos);
        //not Fileinfo.info, seems stack.property imp.property? don't understand how info.info turns into lable of slice. but,
        //stack.getSliceLabels(); does work with stack but not with virtual stack use getSliceLabel to get each data.
        
        double[] asliceposarray=new double[] {0,0,0};//xyz
        ImageStack stack = imp.getStack();
        String label = stack.getSliceLabel(imp.getCurrentSlice());
        //IJ.log(label);
        
        if(label!=null)
        {
            //IJ.log("label info 1st slice; " + label);
            String[] infoarray=label.split("\n|,");//split by \n. stack has original tiff name at 1st line.(virtual stack dont have)
            //May need use BR or something
            for(int i=0;i<infoarray.length;i++)
            {
                if(infoarray[i].indexOf("=")>0)//if this contains =, which means x,y,zpos, not xxxx.tif.
                {
                    String key_=infoarray[i].split("=")[0];
                    double value=Double.parseDouble(infoarray[i].split("=")[1]);
                    //IJ.log(" key "+ key_+" value "+String.valueOf(value));
                    if(key_.equals("xpos"))
                    {
                        asliceposarray[0]=value;
                    }
                    else if(key_.equals("ypos"))
                    {
                        asliceposarray[1]=value;
                    }
                    else if(key_.equals("zpos"))
                    {
                        asliceposarray[2]=value;
                    }
                }
            }
        }
        else
        {
            IJ.log("info null");
        }
        
        IJ.log(String.valueOf(asliceposarray[0])+String.valueOf(asliceposarray[1])+String.valueOf(asliceposarray[2]));
        
        return asliceposarray;
    }
    
    
    ArrayList<Roi> scanImage(ImageProcessor ip_)
    {
    	return scanImage(ip_, false);
    }
    ArrayList<Roi> scanImage(ImageProcessor ip_, boolean debug)
    {
    	ImageProcessor ipwork=ip_.duplicate();
    	ArrayList<Roi> retrunval=new ArrayList<Roi>();
    	
        ImagePlus imp_=new ImagePlus("work", ipwork);
        //RankFilters rf = new RankFilters();
        //rf.rank(ip_, 5, 4);// radius 5, median filter (4)
        //this rankfileter seems not working in postprocessing at tpf.?
        //anyway eliminate fornow -> it makes lot fast
        if(debug)
        {
        	//ImagePlus tesimp=new ImagePlus("tes ", ipwork.duplicate());
        	//tesimp.show();
        }
        ImageStatistics imstat;
        ipwork.threshold(fieldvaluearray[2]);////this convert binary image 0 or 255. but it still 8 bit or 16 bit format;

        Wand wand= new Wand(ipwork);
        wand.setAllPoints(true);
        int minimamarea=fieldvaluearray[0];
        int maximamarea=fieldvaluearray[1];
        int x;
        int y;
        //ip_work.setValue(fillingvalue);
        //ArrayList<Roi> preroiarraylist=roiarraylist;
        Roi roi;
        byte[] pixels;
        if(imp.getBitDepth()!=8)
        {
        	//pixels=(byte[])(ip_work.convertToByte(true).getPixels());
        	ipwork=ipwork.convertToByte(false);
        	//IJ.log("convert to byte");
        }
        pixels=(byte[])ipwork.getPixels();
        //imp_work.setProcessor(ip_work);
        ipwork.setValue(255);
        int samplingstep=10;
        //if(minimamarea<1000)
        	//samplingstep=minimamarea/100;
        for(y=0; y<height; y=y+samplingstep)
        {
            for(x=0; x<width; x=x+samplingstep)
            {
                //IJ.log(String.valueOf(pixels[y*width+x]));
                if(pixels[y*width+x]==0)
                {
                    wand.autoOutline(x,y,0.0,1.0,8);
                    //type polygonroi=2
                    roi = new PolygonRoi(wand.xpoints, wand.ypoints, wand.npoints, 2);
                    //print(x+" "+y);
                    imp_.setRoi(roi);
                    imstat=imp_.getStatistics(1);//area 1 mean 2
                    //
                    if(debug)
                    {
                    	IJ.log("Area "+String.valueOf(imstat.area));
                    	IJ.log(""+String.valueOf(minimamarea)+" "+String.valueOf(maximamarea));
                    }
                    if(imstat.area>minimamarea && imstat.area<maximamarea)
                    {
                        if(debug)
                        {
                        	IJ.log("area "+String.valueOf(imstat.area));
                        }
                        //roiarray=roiarray+
                        //roiarray[counter]=roi;
                    	retrunval.add(roi);
                    }
                    ipwork.fill(roi);
                }
            }
        }    	
        if(debug)
        {
        	ImagePlus tesimp=new ImagePlus("tes ", ipwork.duplicate());
        	tesimp.show();
        	Overlay overlay=new Overlay();
        	for(int i=0;i<retrunval.size();i++)
        	{
        		overlay.add(retrunval.get(i));
        	}
        	tesimp.setOverlay(overlay);
        }
    	
    	return retrunval;
    }
    
    /*---------------------------------------  start process-------------------------------------------------*/
    
    //////////////////////////////////////////////////////////////////////////////////
    public void startProcess(String arg) {
    //IJ.log("startprocess "+ String.valueOf(periodsdat[0]));
    //for(int k=0; k<periodsdat.length/2;k++)
    //test 1st period
    //for(int k=0; k<3;k++)
    //{
    //for(int i=0; i<periodsdat[k*2+1]-periodsdat[k*2]+1;i++)
    for(int i=0; i<slicenumber;i++)	
    {
        
        //imp.setSlice(periodsdat[k*2]+1+i);
        imp.setSlice(i+1);
        //IJ.log(String.valueOf(periodsdat[k*2]+1+i));
        IJ.log(String.valueOf(i+1));
        
        
        
        
        ImageProcessor ip_ori=ip.duplicate();
        
        ip_work=ip.duplicate();
        imp_work=new ImagePlus("work", ip_work);
        RankFilters rf = new RankFilters();
        rf.rank(ip_work, 5, 4);// radius 5, median filter (4)
        ArrayList<Roi> roiarraylist=scanImage(ip_work);
        //if(i==57)
        //{
        	//ArrayList<Roi> roiarraylist=scanImage(ip_work,true);
        	//roiarraylist=scanImage(ip_work,true);
        	//return;
        //}
        //if(i==89)
        //{
        //    roiarraylist=scanImage(ip_work,true);
        //	Roi temptargetroi=(Roi)roiarraylist.get(0);
        //	IJ.log("roilength "+String.valueOf(temptargetroi.getLength()));
        	//ImagePlus imp_check=new ImagePlus("imp_check89", ip);
        	//imp_check.show();
        //}
        
        imp_work.setProcessor(ip_ori);
        //imp_work.show();
        //ImageWindow iw = imp_work.getWindow();
        //ic= iw.getCanvas();
        //ic.addMouseListener(this);
        
        //need to put logic if there are multiple rois are detected. change roiarraylist.get(0) to adequate code
        //IJ.log("roi num "+String.valueOf(roiarraylist.size()));
        Roi targetroi=null;
        int closestindex=0;
        if(roiarraylist.size()==1)
        {
        	targetroi=(Roi)roiarraylist.get(0);
        }
        else if(roiarraylist.size()>1)
        {
        	/*
        	double[][] centerofrois=new double[roiarraylist.size()][2];
        	for(int j=0; j<roiarraylist.size(); j++)
        	{
        		targetroi=(Roi)roiarraylist.get(j);
            	Rectangle bound=targetroi.getBounds();
            	centerofrois[j]= new double[] {bound.getCenterX(),bound.getCenterY()};
        	}*/
        	double[] previouscenter = new double[]{0,0};
        	//if it is the first slice, use clicked pos
        	//if(k==0 && i==0)
        	if(i==0)
        	{
        		//previouscenter=tpf.clickedxy;//clickedxy is null
        		previouscenter=new double[]{width/2, height/2};//use centor for now
        	}
        	else
        	{
            	//Roi previousroi=tpf.trackedroi[periodsdat[k*2]+i];
            	Roi previousroi=tpf.trackedroi[i];
            	Rectangle bound=previousroi.getBounds();
            	previouscenter= new double[] {bound.getCenterX(),bound.getCenterY()};        		
        	}
        	tpf.trackedroi[i]=null;//for saving RAM
        	IJ.log(String.valueOf(previouscenter[0])+" "+String.valueOf(previouscenter[1]));
            
        	//closestindex=closestPoint(previouscenter, centerofrois);
        	closestindex=closestRoi(previouscenter, roiarraylist);
            targetroi=(Roi)roiarraylist.get(closestindex);
        }
        
        
        //if(true){return;}
        
        
        double[] feretdat=new double[5];
        double[] feretxy2={0};
        if(targetroi!=null)
        {
        	//head tail detection by roi shape. newer mode.
        	java.awt.Polygon pol=targetroi.getPolygon();
            //OutlinedShape os=new OutlinedShape(wand.xpoints, wand.ypoints, wand.npoints);
        	//if(i==0)
            if(i>=0)
        	{
        		OutlinedShape os=
        			new OutlinedShape(pol.xpoints, pol.ypoints, pol.npoints, imp);
        		//tpf.osarray[i]=os;
        		//IJ.log("os made");
        		boolean arethereend=os.detectEnds();
        		tpf.sharpnessofheadandtail[i]=new double[]{os.roundendmaxanglediff,
        				os.sharpendmaxanglediff};
        		//tpf.wmarray[i]=os.wm;
        		tpf.wmarray[i]=os.createWormmodel();
        		
        		//if(i==90)return;
        		
        		if(arethereend==true)
        		{
        			/*
            	if(i>0)
            	{
            		int[][] predata=tpf.headtailslicedata2[i-1];
            		if(predata[0][0]>0 && predata[0][1]>0)
            		{
            			//check if the detected head tail is how far from previous ones.
            			//if it seems swapped, change it
            			// this method has problem when swappped wrong way

            			//head to head, head to tail... distence
            			double htoh=Math.sqrt(Math.pow(predata[0][0]-os.roundendxy[0],2)
            					+Math.pow(predata[0][1]-os.roundendxy[1],2));
            			double htot=Math.sqrt(Math.pow(predata[0][0]-os.sharpendxy[0],2)
            					+Math.pow(predata[0][1]-os.sharpendxy[1],2));
            			double ttot=Math.sqrt(Math.pow(predata[1][0]-os.sharpendxy[0],2)
            					+Math.pow(predata[1][1]-os.sharpendxy[1],2));
            			double ttoh=Math.sqrt(Math.pow(predata[1][0]-os.roundendxy[0],2)
            					+Math.pow(predata[1][1]-os.roundendxy[1],2));
            			if(htoh>htot && ttot>ttoh)
            			{
            				int[] tempround=os.roundendxy;
            				os.roundendxy=os.sharpendxy;
            				os.sharpendxy=tempround;
            				IJ.log("swap");
            				//return;
            			}
            		}

            	}*/
        			//else if(predata[0][0]>0 && predata[0][1]>0 && )
        			/*
                Overlay overlay= new Overlay();
                //OvalRoi sharpendroi= new OvalRoi(os.sharpendxy[0]-5,os.sharpendxy[1]-5, 10,10);
                TextRoi sharpendroi= new TextRoi(os.sharpendxy[0]-5,os.sharpendxy[1]-5, "X");
                sharpendroi.setStrokeColor(Color.blue);
                overlay.add(sharpendroi);

                OvalRoi roundendroi= new OvalRoi(os.roundendxy[0]-5,os.roundendxy[1]-5, 10,10);
                roundendroi.setStrokeColor(Color.red);
                overlay.add(roundendroi);

                OvalRoi midroi= new OvalRoi(os.midxy[0]-5,os.midxy[1]-5, 10,10);
                midroi.setStrokeColor(Color.green);
                overlay.add(midroi);
        			 */

        			//show wormmodel   
        			/*
                PolygonRoi wormjoints = os.wm.getJoints();
                wormjoints.setStrokeColor(Color.yellow);
                overlay.add(wormjoints);
                for(int j=0;j<os.wm.bornnum;j++)
                {
                	PolygonRoi wormhead = os.wm.getAnarea(j,os.wm.joint);
                	wormhead.setStrokeColor(Color.yellow);
                	overlay.add(wormhead);
                }
                //os.wm.calcPicdensity();
        			 * 
        			 */
        			//if(true){return;}
        			//imp.setOverlay(overlay);  


        			//tpf.headtailslicedata2[i]=new int[][] {os.roundendxy, os.sharpendxy,os.midxy};
        			//tpf.calBend(i);
        		}
            }
        	else
        	{
        		Wormmodel prewm=tpf.wmarray[i-1];
        		//IJ.log("bodylength "+String.valueOf(prewm.bodylength));
        		Wormmodel wm=new Wormmodel(prewm, imp);
        		tpf.wmarray[i]=wm;
        		
        	}
            //IJ.log("BFT");
            //if(true){return;}
            //if(i==2){return;}
            
            /*
        	//old way
            //head and tail decision using feret
            feretdat=targetroi.getFeretValues();
            //IJ.log("Feret "+ String.valueOf(feretdat[0])+"FeretAngle " + String.valueOf(feretdat[1])+  "MinFeret " + String.valueOf(feretdat[2])+"\nFeretX " + String.valueOf(feretdat[3])+  "FeretY "+ String.valueOf(feretdat[4]));
            double feretx2=0;
            double ferety2=0;
            //feret angle and xy is wired... need to change angle 
            if(feretdat[1]<90)
            {
                feretx2=Math.cos(Math.PI*feretdat[1]/180)*feretdat[0]+feretdat[3];
                ferety2=feretdat[4]-Math.sin(Math.PI*feretdat[1]/180)*feretdat[0];
            }
            else
            {
                feretx2=Math.cos(Math.PI*feretdat[1]/180)*feretdat[0]*-1+feretdat[3];
                ferety2=feretdat[4]+Math.sin(Math.PI*feretdat[1]/180)*feretdat[0];
            }
            feretxy2=new double[]{feretx2, ferety2};
            //IJ.log("x2 "+feretx2+" y2 " +ferety2);
            
            double[][] headtailpoints=new double[][]{{0}};//head xy and tail xy
            //if(k==0 && i==0)//at first slice, compare with clicked location
            if(i==0)//at first slice, compare with clicked location
            {
                double[][] feretends=new double[][]{{feretdat[3],feretdat[4]},feretxy2};
                //int closestpoint=closestPoint(tpf.clickedxy,feretends);
                int closestpoint=closestPoint(new double[] {0,0},feretends);
                //IJ.log("closestpoint "+String.valueOf(closestpoint));
                headtailpoints=new double[][] {feretends[closestpoint],feretends[(closestpoint+1)%2]};
                //atan2(y,x)
                //IJ.log("headtailpoints h "+String.valueOf(headtailpoints[0][0])+" "+String.valueOf(headtailpoints[0][1])+" t "+String.valueOf(headtailpoints[1][0])+" "+String.valueOf(headtailpoints[1][1]));
                //theta[periodsdat[k*2]+1+i]=Math.atan2(headtailpoints[0][1]-headtailpoints[1][1], headtailpoints[0][0]-headtailpoints[1][0]);
                theta[i+1]=Math.atan2(headtailpoints[0][1]-headtailpoints[1][1], headtailpoints[0][0]-headtailpoints[1][0]);
                //IJ.log("theta "+String.valueOf(theta[i+1]/Math.PI*180));
                //pretheta is required for gap between periods
                //pretheta=theta[periodsdat[k*2]+1+i];
                pretheta=theta[i+1];
            }
            else//use previous data
            {
                //feret angle is calculated as opposit way need to change direction
                //theta[periodsdat[k*2]+1+i]=getSmallerAngle(pretheta, Math.PI*feretdat[1]/180);//feretdat[1]=feret angle
                //theta[periodsdat[k*2]+1+i]=getSmallerAngle(pretheta, Math.PI-Math.PI*feretdat[1]/180);//feretdat[1]=feret angle
                //pretheta=theta[periodsdat[k*2]+1+i];
                theta[i+1]=getSmallerAngle(pretheta, Math.PI-Math.PI*feretdat[1]/180);//feretdat[1]=feret angle
                pretheta=theta[i+1];
                //IJ.log("theta "+String.valueOf(theta[i+1]/Math.PI*180));
                //assignDirection(double[][] points, double theta)
                headtailpoints=assignDirection(new double[][] {{feretdat[3],feretdat[4]}, feretxy2},theta[i+1]);
                //IJ.log("headtailpoints h "+String.valueOf(headtailpoints[0][0])+" "+String.valueOf(headtailpoints[0][1])+" t "+String.valueOf(headtailpoints[1][0])+" "+String.valueOf(headtailpoints[1][1]));
            }
            */
            /*
            Overlay overlay= new Overlay();
            //roiarraylist.get(0).setStrokeColor(Color.orange);
            targetroi.setStrokeColor(Color.orange);
            overlay.add(targetroi);
            //imp_work.setRoi((Roi)roiarraylist.get(0));
            
            OvalRoi headcircleroi= new OvalRoi((int)headtailpoints[0][0]-2,(int)headtailpoints[0][1]-2, 5,5);
            headcircleroi.setStrokeColor(Color.red);
            overlay.add(headcircleroi);
            
            imp.setOverlay(overlay);
            */
            //imp_work.updateAndDraw();
            /*
        	tpf.roidetected[periodsdat[k*2]+i]=true;//roidetected startfrom 0.
            tpf.trackeddata[periodsdat[k*2]+1+i]=feretdat;
            IJ.log("tracked data");
            //tpf.trackedroi.add(periodsdat[0]+1+i,roiarraylist.get(0));
            tpf.trackedroi[periodsdat[k*2]+1+i]=targetroi;
            tpf.headtailslicedata[periodsdat[k*2]+1+i]=headtailpoints;
            */
        	tpf.roidetected[i+1]=true;
            //tpf.trackeddata[i+1]=feretdat;
            //IJ.log("tracked data");
            //tpf.trackedroi.add(periodsdat[0]+1+i,roiarraylist.get(0));
            tpf.trackedroi[i+1]=targetroi;
            //tpf.headtailslicedata[i+1]=headtailpoints;
            
            tpf.stagepos[i]=getSlicepos();
            //IJ.log("tracked roi");
        }
    }//for(int i=0; i<periodsdat[1]-periodsdat[0]+1;i++)end
    
    //}//for(int k=0; k<periodsdat.length/2;k++)end
    IJ.log("Process done");
    ImageWindow iw = imp.getWindow();
    iw = imp.getWindow();
    ic= iw.getCanvas();
    ic.addMouseListener(tpf);
	
	//tpf.plot=new Plot("plot","x","deltaangle",new double[]{0},new double[]{0});
	tpf.filteringWm();
    //tpf.plot=new Plot("plot","x","velocity",new double[]{0},new double[]{0});
	//tpf.pw=tpf.plot.show();
    //tpf.velocity2=tpf.calVelocity2();
    //tpf.velocity3=tpf.calVelocity3();
    //tpf.plotVel();
    }//public void startProcess(String arg) {end
}