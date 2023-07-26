# BFTracker

Behavioural Tracker Analysis for bright field full field images. (See branch v2-L1Worms for adjusted code for smaller, more transparent L1 worms)

Use virtural stack taken by Realtimetracker_09 or after bright field full field images (needs higer than imagej 1.44k)
1. open virtual stack
2. start BFT plugin
3. click Setup and set threshold and roi size (min,max). this filed has to be hit return/enter key
4. choose scale 10x 4x
5.  Go
6.  check if head and tail are detected correctly.
7. If not, click swap botton to change all frame, or click the graph to swap each period, or click each slice image one by one.
8.  output data and curvature. the unit of angles is radian
9. 20607 gave up to track coiling situation (touching head or tail to the body)
could be detectable omegaturn by combination of reverse/deepbend/touching.
just transferrig .jar file made by eclipse to plugin folder of imagej didn't work
transfer /bin/*.class to plugin/BFT did work.

