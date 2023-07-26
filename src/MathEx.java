import java.util.*;

//utility for ease of calculation

public class MathEx {
	
	//range
	static double[] range(double[] vector)
	{
		double min=vector[0];
		double max=vector[0];
		for(int i=1; i<vector.length;i++)
		{
			if(min>vector[i])
				min=vector[i];
			if(max<vector[i])
				max=vector[i];
		}
		return new double[]{min, max};
	}
	
	//min
	static double[] min(double[] vector)
	{
		double min=vector[0];
		int minindex=0;

		for(int i=1; i<vector.length;i++)
		{
			if(min>vector[i])
			{
				min=vector[i];
				minindex=i;
			}
		}
		return new double[]{min, minindex};
	}

	//max
	static double[] max(double[] vector)
	{
		double max=vector[0];
		int maxindex=0;
		for(int i=1; i<vector.length;i++)
		{
			if(max<vector[i])
			{
				max=vector[i];
				maxindex=i;
			}
		}
		return new double[]{max, maxindex};
	}
	//for loop could be faster than Listiterator, but anyway use it for now
	static double[] max(ArrayList<Double> list)
	{
		double max=list.get(0);
		int maxindex=0;
		ListIterator<Double> iterator = list.listIterator(); 
	    while(iterator.hasNext())
	    {
	    	double value=iterator.next();
			if(max<value)
			{
				max=value;
				maxindex=iterator.nextIndex()-1;
			}
	    } 
	    return new double[]{max, maxindex};
	}
	//sum
	static double sum(double[] vector)
	{
		double sum=0;
		for(int i=0; i<vector.length;i++)
		{
			sum=sum+vector[i];
		}
		return sum;		
	}
	static double sum(ArrayList<Double> list)
	{
		double sum=0;
		ListIterator<Double> iterator = list.listIterator(); 
	    while(iterator.hasNext())
	    {
	    	sum=sum+=iterator.next();
	    } 
	    return sum;
	}

	//mean
	static double mean(double[] vector)
	{
		double sum=0;
		for(int i=0; i<vector.length;i++)
		{
			sum=sum+vector[i];
		}
		return sum/vector.length;
	}
	
	//mean for [index][x,y] format
	static double[] mean(double[][] vector)
	{
		double sumx=0;
		double sumy=0;
		for(int i=0; i<vector.length;i++)
		{
			sumx=sumx+vector[i][0];
			sumy=sumy+vector[i][1];
		}
		return new double[] {sumx/vector.length,sumy/vector.length};
		
	}
	//mean for [index][x,y] format
	static double[] mean(int[][] vector)
	{
		double sumx=0;
		double sumy=0;
		for(int i=0; i<vector.length;i++)
		{
			sumx=sumx+vector[i][0];
			sumy=sumy+vector[i][1];
		}
		return new double[] {sumx/vector.length,sumy/vector.length};
		
	}
	

	
    //median
    static double median(double[] vector)
    {
        double[] temparray=new double [vector.length];//To do deep copy, make new instance.
        for(int i=0; i<vector.length;i++)
        {
            temparray[i]=vector[i];
        }
        java.util.Arrays.sort(temparray);
        int middle=temparray.length/2;
        return temparray[middle];
    }
    
	//square
	static double[] square(double[] vector)
	{
        double[] temparray=new double [vector.length];//To do deep copy, make new instance.
        for(int i=0; i<vector.length;i++)
        {
            temparray[i]=vector[i]*vector[i];
        }
        return temparray;
	}
    
    
    //calculate distance between two points
	static float getDistance(int[] a, int[] b)
	{  
		return (float)Math.sqrt((a[0]-b[0])*(a[0]-b[0])+(a[1]-b[1])*(a[1]-b[1]));
	}
	
    //return closest index of targets array. ori and target shoul have 2 values
    //-1 is abnormal return val
    static int closestPoint(double[] ori, double[][] targets)
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
    
    static int closestPoint(int[] ori, int[][] targets)
    {
    	double[][] dtargets=new double[targets.length][2];
    	for(int i=0;i<targets.length;i++)
    	{
    		dtargets[i][0]=targets[i][0];
    		dtargets[i][1]=targets[i][1];
    	}
    	
    	return closestPoint(new double[]{ori[0],ori[1]},dtargets);
    }
    
    //return closestindex and the distance
    static double[] closestIndexandDistance(double[] ori, double[][] targets)
    {
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
         return new double[] {closestindex,mindistance};
    }
    static double[] closestIndexandDistance(int[] ori, int[][] targets)
    {
    	double[][] dtargets=new double[targets.length][2];
    	for(int i=0;i<targets.length;i++)
    	{
    		dtargets[i][0]=targets[i][0];
    		dtargets[i][1]=targets[i][1];
    	}
    	return closestIndexandDistance(new double[]{ori[0],ori[1]},dtargets);
    }
    
    /*int version*/
	//inner product of two vector; a and b. for 2 dimension
	static int getInner2D(int ax, int ay, int bx, int by)
	{
		return ax*bx+ay*by;
	}

	//outer product for 2 dimension
	static int getOuter2D(int ax, int ay, int bx, int by)
	{
		return ax*by-ay*bx;
	}
	
	//difference of two vectors. radian. positive vales indicate counter clockwise.
	static double getAngleDiff(int ax, int ay, int bx, int by)
	{
		return Math.atan2(getOuter2D(ax, ay, bx, by), getInner2D(ax, ay, bx, by));
	}
	
	/*double version*/
	//inner product of two vector; a and b. for 2 dimension
	static double getInner2D(double ax, double ay, double bx, double by)
	{
		return ax*bx+ay*by;
	}

	//outer product for 2 dimension
	static double getOuter2D(double ax, double ay, double bx, double by)
	{
		return ax*by-ay*bx;
	}
	
	//difference of two vectors. radian. positive vales indicate counter clockwise.
	static double getAngleDiff(double ax, double ay, double bx, double by)
	{
		return Math.atan2(getOuter2D(ax, ay, bx, by), getInner2D(ax, ay, bx, by));
	}
	
	
	//rotate vector
	static double[] rotVec(double[] xy, double radian)
	{
		return new double[]{xy[0]*Math.cos(radian)-xy[1]*Math.sin(radian),
				xy[0]*Math.sin(radian)+xy[1]*Math.cos(radian)};		
	}
	
	
	
	//boxcar smoothing method. if circ is true, use circuation array as input
    static double[] boxCar(double[] array, int window, boolean circ)
    {
        double[] returnval=new double[array.length];
        for(int i=0;i<array.length;i++)
        {
            double sumoflocal=0;
            //int denominator=window*2+1;
            int denominator=window;
            //for(int k=-window;k<=window;k++)
            for(int k=-(window/2+1);k<=window/2+1;k++)
            {
                if(i+k<0)
                {
                    if(circ)
                    {
                        sumoflocal=sumoflocal+array[array.length+i+k];
                    }
                    else
                    {
                        sumoflocal=sumoflocal+0;
                        denominator=denominator-1;
                    }
                }
                else if (i+k>=array.length)
                {
                    if(circ)
                    {
                        sumoflocal=sumoflocal+array[i+k-array.length];
                    }
                    else
                    {
                        sumoflocal=sumoflocal+0;
                        denominator=denominator-1;
                    }
                }
                else
                {
                    sumoflocal=sumoflocal+array[i+k];
                }
            }
            returnval[i]=sumoflocal/denominator;
        }
        return returnval;
    }
    
    static double[] boxCar(double[] array, int window)
    {
        return boxCar(array, window, false);
    }
    
    
    //running median
    //not implemented yet circ==false
    static double[] runMed(double[] array, int window, boolean circ)
    {
        double[] returnval=new double[array.length];
        //System.arraycopy(vector, i, temparray,0,window);
        if(circ)
        {
        	double[] temparray=new double[window];
        	for(int i=0;i<array.length;i++)
        	{
        		for(int j=0;j<window;j++)
        		{
        			temparray[j]=array[(array.length+i-window/2+j)%array.length];
        		}
        		returnval[i]=median(temparray);
        	}
        }
        else
        {
        	for(int i=0;i<array.length;i++)
        	{
        		double sumoflocal=0;
        		//int denominator=window*2+1;
        		int denominator=window;
        		double[] tempvec=new double[window];
        		for(int k=-(window/2+1);k<=window/2+1;k++)
        		{
        			if(i+k<0)
        			{
        			}
        			else if (i+k>=array.length)
        			{
        			}
        			else
        			{

        			}

        		}
        		//returnval[i]=
        	}
        }
    	return returnval;	
    }
    
    static double[] runMed(double[] array, int window)
    {
        return runMed(array, window, false);
    }

    
    //fit a line by least square
    static double[] ls(double[] x, double[] y)
    {
    	double X, Y, XX, XY;
    	X=Y=XX=XY=0;
    	for(int i=0;i<x.length;i++)
    	{
    		X=X+x[i];
    		Y=Y+y[i];
    		XX=XX+x[i]*x[i];
    		XY=XY+x[i]*y[i];
    	}
    	return new double[]{((double)x.length*XY-X*Y)/((double)x.length*XX-X*X),
    			(XX*Y-XY*X)/((double)x.length*XX-X*X),};

    }
    static double[] ls(int[] x, int[] y)
    {
    	double[] dx, dy;
    	dx=new double[x.length];
    	dy=new double[x.length];
    	for(int i=0;i<x.length;i++)
    	{
    		dx[i]=(double)x[i];
    		dy[i]=(double)y[i];
    		
    	}
    	return ls(dx,dy);
    	
    }

    
    
}
