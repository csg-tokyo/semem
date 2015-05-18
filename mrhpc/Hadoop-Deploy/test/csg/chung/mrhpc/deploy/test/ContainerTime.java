package csg.chung.mrhpc.deploy.test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ContainerTime {

	public List<Long> request, load, run, app1, app2, app3, app4, app5, app6, terminate, startTime;
	
	public ContainerTime(String input, String startwith, long limit) throws IOException{
		request = new ArrayList<Long>();
		load = new ArrayList<Long>();
		run = new ArrayList<Long>();
		app1 = new ArrayList<Long>();
		app2 = new ArrayList<Long>();
		app3 = new ArrayList<Long>();
		app4 = new ArrayList<Long>();
		app5 = new ArrayList<Long>();		
		app6 = new ArrayList<Long>();				
		terminate = new ArrayList<Long>();
		startTime = new ArrayList<Long>();
		
		File dir = new File(input);
		File[] listFiles = dir.listFiles();
		
		long min = -1;
		
		for (int i=0; i < listFiles.length; i++){
			if (listFiles[i].getName().startsWith(startwith)){
				System.out.println(listFiles[i].getName());
				FileReader fr = new FileReader(listFiles[i]);
				BufferedReader read = new BufferedReader(fr);
				
				long start = getLong(read.readLine());
				long requestT = getLong(read.readLine());
				long loadT = getLong(read.readLine());
				long runT = getLong(read.readLine());
				long appT1 = getLong(read.readLine());
				long appT2 = getLong(read.readLine());
				long appT3 = getLong(read.readLine());
				long appT4 = getLong(read.readLine());
				long appT5 = getLong(read.readLine());				
				long appT6 = getLong(read.readLine());								
				long terminateT = getLong(read.readLine());
				
				startTime.add(start);
				if (min == -1 || min > start){
					min = start;
				}
				
				if (requestT != -1){
					request.add(requestT - start);
				}else{
					request.add((long) 0);
				}
				
				if (loadT != -1){
					load.add(loadT - requestT);
				}else{
					load.add((long) 0);
				}
				
				if (runT != -1){
					run.add(runT - loadT);
				}else{
					run.add((long) 0);
				}
				
				if (appT1 != -1){
					app1.add(appT1 - runT);
				}else{
					app1.add((long) 0);
				}
				
				if (appT2 != -1){
					app2.add(appT2 - appT1);
				}else{
					app2.add((long) 0);
				}
				
				if (appT3 != -1){
					app3.add(appT3 - appT2);
				}else{
					app3.add((long) 0);
				}
				
				if (appT4 != -1){
					app4.add(appT4 - appT3);
				}else{
					app4.add((long) 0);
				}
				
				if (appT5 != -1){
					app5.add(appT5 - appT4);
				}else{
					app5.add((long) 0);
				}

				if (appT6 != -1){
					app6.add(appT6 - appT5);
				}else{
					app6.add((long) 0);
				}				
				
				if (terminateT != -1){
					terminate.add(terminateT - appT6);
				}else{
					terminate.add((long) 0);
				}
				
				read.close();
				fr.close();
			}
		}
		
		for (int i=0; i < load.size(); i++){
			load.set(i, load.get(i) + request.get(i));
		}
		
		System.out.println(min);
		for (int i=0; i < startTime.size(); i++){
			startTime.set(i, startTime.get(i) - min);
		}
		
		printOne(startTime);
		printOne(load);
		printOne(run);	
		printOne(app1);
		printOne(app2);
		printOne(app3);
		printOne(app4);
		printOne(app5);		
		printOne(app6);				
	}
		
	public void printOne(List<Long> a){
		for (int i=0; i < a.size(); i++){
			if (i < a.size() - 1){
				System.out.print(a.get(i) + ",");
			}else{
				System.out.print(a.get(i));
			}
		}
		
		System.out.println();
		System.out.println(a.size());
	}
	
	public long getLong(String line){
		if (line == null){
			return -1;
		}
		
		String split[] = line.split(" ");
		
		return Long.parseLong(split[split.length - 1]);
	}
	
	public static void main(String args[]) throws IOException{
		new ContainerTime("/Users/chung/Desktop/terasort-4nodes-origin", "container", 30000);
	}
}
