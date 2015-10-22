package csg.chung.mrhpc.processpool;

import java.util.ArrayList;
import java.util.List;
import csg.chung.mrhpc.utils.MapOutputObj;

public class MapOutputList {
	private List<MapOutputObj> mapOutputList;
	
	public MapOutputList(){
		this.mapOutputList = new ArrayList<MapOutputObj>();		
	}
	
	public synchronized void add(MapOutputObj obj){
		mapOutputList.add(obj);
	}	
	
	public synchronized void remove(String mapID, int rID){
		for (int i=0; i < mapOutputList.size(); i++){
			if (mapOutputList.get(i).getMapID().equals(mapID) && mapOutputList.get(i).getReduceID() == rID){
				mapOutputList.remove(i);
				break;
			}
		}		
	}	
	
	public synchronized byte[] find(String mapID, int rID) {
		for (int i = 0; i < mapOutputList.size(); i++) {
			if (mapOutputList.get(i).getMapID().equals(mapID) && mapOutputList.get(i).getReduceID() == rID) {
				return mapOutputList.get(i).getData();
			}
		}
		
		return null;
	}
}