package csg.chung.mrhpc.processpool;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import csg.chung.mrhpc.utils.Environment;
import csg.chung.mrhpc.utils.Lib;

public class TaskThread extends Thread {
	String properties;
	String className;
	boolean startArg;
	String args[];
	int count;
	
	public TaskThread(String prop, String name) {
		this.properties = prop;
		this.className = name;
		setSystem(properties);		
		args = new String[10];
	}
	
	public TaskThread(String input){
		args = new String[10];
		count = 0;
		startArg = false;
		try {
			FileReader fr = new FileReader(new File(input));
			BufferedReader in = new BufferedReader(fr);
			String line;
			in.readLine();
			while ((line=in.readLine()) != null){
				System.out.println(line);
				lineAnalyze(line);
			}
			in.close();
			fr.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.out.println(this.className);
	}
	
	public void lineAnalyze(String line){
		String split[] = line.split(" ");
		if (line.startsWith("export")){
			Environment.setenv(split[1].split("=")[0], split[1].split("=")[1].replaceAll("\"", ""), false);
		}else
		if (line.startsWith("exec")){
			for (int i=0; i < split.length; i++){
				startJava(split[i]);
			}
		}else{
			if (line.length() > 0){
				Lib.runCommand(line);
			}
		}
	}
	
	public void startJava(String str){
		System.out.println(str);
		if (str.startsWith("-D")){
			String values[] = str.split("=");
			System.setProperty(values[0].substring(2), values.length == 1 ? "":values[1]);			
		}
		if (!str.startsWith("1>") && !str.startsWith("2>") && startArg  == true){
			args[count] = str;
			count++;
		}		
		if (str.startsWith("org")){
			this.className = str;
			startArg = true;
		}
		if (str.startsWith("1>")){
			String values[] = str.split(">");
			try {
				System.setOut(new PrintStream(new File(values[1])));
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		if (str.startsWith("2>")){
			String values[] = str.split(">");
			try {
				System.setErr(new PrintStream(new File(values[1])));
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}		
	}

	public void run() {
		try {
			//String path = "/home/mrhpc/hadoop/etc/hadoop:/home/mrhpc/hadoop/etc/hadoop:.:/home/mrhpc/hadoop/etc/hadoop:/home/mrhpc/hadoop/share/hadoop/common/lib/*:/home/mrhpc/hadoop/share/hadoop/common/*:/home/mrhpc/hadoop/share/hadoop/hdfs:/home/mrhpc/hadoop/share/hadoop/hdfs/lib/*:/home/mrhpc/hadoop/share/hadoop/hdfs/*:/home/mrhpc/hadoop/share/hadoop/yarn/lib/*:/home/mrhpc/hadoop/share/hadoop/yarn/*:/home/mrhpc/hadoop/share/hadoop/mapreduce/lib/*:/home/mrhpc/hadoop/share/hadoop/mapreduce/*:/contrib/capacity-scheduler/*.jar:/contrib/capacity-scheduler/*.jar:/home/mrhpc/usr/lib/mpi.jar:/home/mrhpc/test:/home/mrhpc/test/guava-17.0.jar:/home/mrhpc/test/commons-codec-1.9.jar:/home/mrhpc/hadoop/share/hadoop/yarn/*:/home/mrhpc/hadoop/share/hadoop/yarn/lib/*:/home/mrhpc/hadoop/etc/hadoop/nm-config/log4j.properties";
			//URL[] classpathExt = buildClasspath(path);
			//URLClassLoader loader = new URLClassLoader(classpathExt, null);
			//String prop = "-Dhadoop.log.dir=/home/mrhpc/hadoop/logs -Dyarn.log.dir=/home/mrhpc/hadoop/logs -Dhadoop.log.file=yarn-mrhpc-nodemanager-slave1.log -Dyarn.log.file=yarn-mrhpc-nodemanager-slave1.log -Dyarn.home.dir= -Dyarn.id.str=mrhpc -Dhadoop.root.logger=INFO,RFA -Dyarn.root.logger=INFO,RFA -Dyarn.policy.file=hadoop-policy.xml -server -Dhadoop.log.dir=/home/mrhpc/hadoop/logs -Dyarn.log.dir=/home/mrhpc/hadoop/logs -Dhadoop.log.file=yarn-mrhpc-nodemanager-slave1.log -Dyarn.log.file=yarn-mrhpc-nodemanager-slave1.log -Dyarn.home.dir=/home/mrhpc/hadoop -Dhadoop.home.dir=/home/mrhpc/hadoop -Dhadoop.root.logger=INFO,RFA -Dyarn.root.logger=INFO,RFA";
			//"org.apache.hadoop.yarn.server.nodemanager.NodeManager"
			
			//System.out.println(properties + " " + className);
			Class<?> hello = Class.forName(className);

			Method mainMethod = hello.getMethod("main", String[].class);
			Object[] arguments = new Object[] {args};
			mainMethod.invoke(null, arguments);
			for(;;);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void setSystem(String input){
		String libs[] = input.split(" ");
		for (int i=0; i < libs.length; i++){
			String values[] = libs[i].split("=");
			System.setProperty(values[0].substring(2), values.length == 1 ? "":values[1]);
		}
	}
	
	public URL[] buildClasspath(String input) throws MalformedURLException {
		List<URL> result = new ArrayList<URL>();
		String libs[] = input.split(":");
		
		for (int i=0; i < libs.length; i++){
			libs[i] = libs[i].replace("*", "");
			libs[i] = libs[i].replace("*.jar", "");			
			File dir = new File(libs[i]);
			File[] listFile = dir.listFiles();
			if (listFile != null){
				System.out.println(libs[i] + ": " + listFile.length);
				for (int j = 0; j < listFile.length; j++) {
					if (listFile[j].getName().endsWith(".jar")) {
						//System.out.println(listFile[j]);
						result.add(listFile[j].toURI().toURL());
					}
				}
			}
		}
		
		return result.toArray(new URL[result.size()]);
	}
}