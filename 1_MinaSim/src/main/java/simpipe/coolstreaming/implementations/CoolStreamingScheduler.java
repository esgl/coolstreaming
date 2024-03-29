package simpipe.coolstreaming.implementations;

import se.peertv.peertvsim.core.Timer;
//import simpipe.coolstreaming.interfaces.Scheduler;
import se.peertv.peertvsim.core.Scheduler;
import simpipe.coolstreaming.BitField;
import simpipe.coolstreaming.Constants;
import simpipe.coolstreaming.ControlRoom;
import simpipe.coolstreaming.PeerNode;

import java.util.ArrayList;

/*
 * -This class is designed for scheduling which segment will be fetched from which partner
 * using the CoolStreaming (paper) algorithm without the "Transfer Time" optimization
 * -Also it holds the buffer map for the node initiating this object 
 */
public class CoolStreamingScheduler implements simpipe.coolstreaming.interfaces.Scheduler {
	
	private PeerNode node;
	private int startTime;
	private int wholeBits[];
	private int deadLine[];
	//private int supplier[];
	private int slack=10;
	private int exchangeTime=10000;//request map every 30 sec
	private int timeSlot;
	private boolean requesting=false;
	
	
	@Override
	public void fillDeadLine() {
		deadLine=new int[node.getVideoSize()];
		int start=startTime/1000;	//in seconds
		for(int i=0;i<deadLine.length;i++){
			deadLine[i]=start+i+slack;
		}
	}

	@Override
	public boolean isValid(int index, int timeNow) {
		if(deadLine[index]<timeNow)
			return true;
		return false;
	}
	
	@Override
	public BitField beginscheduling() {
		BitField field = new BitField(node.getWindowSize());
		ArrayList<Integer> dupSet [] =new ArrayList[node.getPSize()+1];
		ArrayList<Integer> tempSupplierSet [] =new ArrayList[node.getWindowSize()];
		int timeNow=(int)Scheduler.getInstance().now;
		BitField window=new BitField(node.getWindowSize());
		window=getWindow(timeSlot); 
		for (int n=1;n<=node.getPSize();n++){
			dupSet[n]=new ArrayList();
		}
		for(int i=0;i<node.getWindowSize();i++){
			tempSupplierSet[i]=new ArrayList();
			if (window.bits[i]==1)
				continue;
			int nlength=0;
			for(int j=0;j<node.getPSize();j++){
				if(node.getPartners().getPartner(j)==null)
					continue;
				if(timeSlot!=node.getPartners().getPartner(j).bufferMap.time)
					continue;
				if(node.getPartners().getPartner(j).bufferMap.bits[i]==1&&isValid(i,timeNow)){
					tempSupplierSet[i].add(node.getPartners().getPartner(j).port);
					nlength++;}
			}
			if (nlength==0)
				continue;
			else if (nlength ==1){
				field.a1.add(i);
				field.a2.add(tempSupplierSet[i].get(0));}
			else 
				{dupSet[nlength].add(i);}
		}
		for (int nlength = 2;nlength<=node.getPSize();nlength++){
			int bandwidth[]=new int[nlength];
			for (int i=0;i<dupSet[nlength].size();i++){
				for(int counter=0;counter<nlength;counter++){
					int pos=node.getPartners().getIndex(tempSupplierSet[dupSet[nlength].get(i)].get(counter));
					if(pos!=-1)
					bandwidth[counter]=node.getPartners().getPartner(pos).bandwidth;
					else
					bandwidth[counter]=0; //node.defaultBandwidth;
				}
				int supplier=pickPeer(bandwidth);
				field.a1.add(dupSet[nlength].get(i));
				field.a2.add(tempSupplierSet[dupSet[nlength].get(i)].get(supplier));
			}
		}
			
		return field;
	}

	@Override
	public void exchangeBM(int dull) {
		int milliesNow=(int)Scheduler.getInstance().now;	
    //	int secondNow=milliesNow/1000;
    	timeSlot = milliesNow;
    	/*for(int i=0;i<node.videoSize;i++)
	    	if(wholeBits[i]==0&&deadLine[i]<secondNow){
	    		timeSlot=startTime+(i*1000);
	    		break;
	    	}*/
    	requesting=true;
    	for(int i=0;i<node.getPSize();i++)
    		if(node.getPartners().getPartner(i)!=null){
    		node.getPartners().getPartner(i).session.write(""+Constants.BUFFERMAP_REQUEST+timeSlot);
   		}
    	
    	try {
			new Timer(exchangeTime,this,"exchangeBM",0);
		} catch (Exception e) {
		e.printStackTrace();
		}
	}
	
	@Override
	public BitField getWindow(int now) {
		BitField bits = new BitField(node.getWindowSize());
		int diff=now-startTime;
		diff=diff/1000; //conv to sec
		int j=0;
		for(int i=(diff / node.getWindowSize())* node.getWindowSize();i<node.getWindowSize()+((diff / node.getWindowSize())* node.getWindowSize());i++){
			if(j==node.getWindowSize())
				break;
			if(i<node.getVideoSize())
				bits.setBit(j,wholeBits[i]);
			else
				bits.setBit(j,0);
			
			j++;
		}
		return bits;
	}

	@Override
	public void identifyRequiredSegments() {
		if(requesting){
			requesting=false;
			int diff=(timeSlot - startTime)/1000;
			
			// is the movie finished
			if(diff >= node.getVideoSize())
				return;
			
			BitField field = beginscheduling();
			
			int loc = 0;
			for(int i=0;i < field.a1.size();i++){
			/*	// is the movie finished
				if(diff+i >= node.videoSize)
					return;
				
				if(wholeBits[diff+i]==1 || field.bits[i]==0)
					continue; */

				loc = node.getPartners().getIndex(field.a2.get(i));
				if(loc==-1)
					continue;
				int sum=field.a1.get(i)+(diff / node.getWindowSize())*node.getWindowSize();
				node.getPartners().getPartner(loc).session.write(""+Constants.SEGMENT_REQUEST+sum);
			}
		}
		
	}

	
	@Override
	public  synchronized int pickPeer(int[] bandwidth) {
		int maxPartner=0;
		int max=bandwidth[0];
		for(int i=0;i<bandwidth.length-1;i++){
			if(max>bandwidth[i+1])
				continue;
			else 
				{max=bandwidth[i+1];
				maxPartner=i+1;}
		}
		
		return maxPartner;
	}

	@Override
	public void setParams(PeerNode node, int startTime) {
		if(ControlRoom.isAutomated){
			slack=ControlRoom.slack;
			exchangeTime = ControlRoom.exchange;
			node.setWindowSize(ControlRoom.windowSize);
		}
		this.node=node;
		this.startTime = startTime;
		fillDeadLine();
		wholeBits=new int[node.getVideoSize()];
		//supplier=new int[node.windowSize];
		if(node.isSource())
		{
			for(int i=0;i<node.getVideoSize();i++){
				wholeBits[i]=1;
			}
		}

	}

	@Override
	public void setStartTime(int start) {
		startTime=start;

	}

	@Override
	public void setWholeBits(int index, int value) {
		wholeBits[index]=value;

	}
	@Override
	public int getDeadLine(int index) {
		return deadLine[index];
	}

	@Override
	public int getExchangeTime() {
		return exchangeTime;
	}

	@Override
	public int getWholeBits(int index) {
		return wholeBits[index];
	}
	public void subscribe(char header){
		
	}
	public void fire(char header){
		
	}

}
