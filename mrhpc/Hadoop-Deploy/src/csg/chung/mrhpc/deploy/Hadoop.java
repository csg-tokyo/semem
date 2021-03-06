package csg.chung.mrhpc.deploy;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.util.ArrayList;
import java.util.List;

import mpi.Info;
import mpi.Intercomm;
import mpi.MPI;
import mpi.MPIException;
import mpi.Request;

public class Hadoop {
	public final static String HADOOP_FOLDER 			= "/home/mrhpc/hadoop";
	private Intercomm spawn[];
	private List<Intercomm> mrSpawn;
	private List<Request> mrRequest;
	private List<CharBuffer> mrMessage;
	private int rank, size, numberSlaves;
	
	public Hadoop(){
		try {
			rank = MPI.COMM_WORLD.getRank();
			size = MPI.COMM_WORLD.getSize();
			numberSlaves = size - 1;
			spawn = new Intercomm[numberSlaves];
			mrSpawn = new ArrayList<Intercomm>();
			mrRequest = new ArrayList<Request>();
			mrMessage = new ArrayList<CharBuffer>();
			
			// Print node info
			InetAddress ip = InetAddress.getLocalHost();
			System.out.println("P" + rank + "/" + size + ": " + ip.getHostName() + " - " + ip.getHostAddress());	
						
			if (rank == 0){
				// Master node
				startMaster(rank);
			}else{
				// Slaves node
				startSlaves(rank);
			}
			
			// Spawn
			if (numberSlaves > 0) {
				spawnOnSlaves(rank, numberSlaves);
				Request request[] = new Request[numberSlaves];
				CharBuffer message[] = new CharBuffer[numberSlaves];
				for (int i = 0; i < numberSlaves; i++){
					message[i] = ByteBuffer.allocateDirect(500).asCharBuffer();
					request[i] = spawn[i].iRecv(message[i], 500, MPI.CHAR, 0, Constants.TAG);
				}
				while (true) {
					for (int i = 0; i < numberSlaves; i++) {
						if (request[i].test()){
							String cmd = message[i].toString().trim();
							System.out.println("slave" + (i+1) + " start spawning Grand Child: " + cmd);
							spawnGrandChild(cmd, spawn[i], 0, "slave" + (i+1));
							message[i] = ByteBuffer.allocateDirect(500).asCharBuffer();
							request[i] = spawn[i].iRecv(message[i], 500, MPI.CHAR, 0, Constants.TAG);
						}
					}
					
					int count = 0;
					while (count < mrSpawn.size()){
						if (mrRequest.get(count).test()){
							System.out.println("Count: " + count);
							String path = mrMessage.get(count).toString().trim();
							System.out.println("Receive: " + path);
							CharBuffer mes = ByteBuffer.allocateDirect(500).asCharBuffer();
							Request req = mrSpawn.get(count).iRecv(mes, 500, MPI.CHAR, 0, Constants.TAG);
							
							mrMessage.set(count, mes);
							mrRequest.set(count, req);							
							// Read file here
							readAndSend(mrSpawn.get(count), path);
						}
						count++;
					}
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		} catch (MPIException e) {
			e.printStackTrace();
		}
	}
	
	public void readAndSend(Intercomm group, String path){
		try {
			File file = new File(path);
			byte[] bytes = new byte[(int) file.length()];
			FileInputStream fileInputStream = new FileInputStream(file);
			fileInputStream.read(bytes);
			fileInputStream.close();	
			System.out.println("Sending back... --> Length: " + bytes.length);
			int length[] = new int[1];
			length[0] = bytes.length;
			group.send(length, 1, MPI.INT, 0, Constants.TAG);
			group.send(bytes, bytes.length, MPI.BYTE, 0, Constants.TAG);
			System.out.println("Sending OK.");
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (MPIException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public class SpawnConnection extends Thread{
		private Intercomm spawn;
		private String host;
		private int child;
		
		public SpawnConnection(Intercomm spawn, int child, String host){
			this.spawn = spawn;
			this.host = host;
			this.child = child;
		}
		
		public void run(){
			while(true){
				receiveSpawnFromChild(spawn, child, host);
			}
		}
	}
	
	public void startMaster(int rank){
		runCommand(HADOOP_FOLDER + "/sbin/hadoop-daemon.sh start namenode");
		runCommand(HADOOP_FOLDER + "/sbin/yarn-daemon.sh start resourcemanager");
		System.out.println("Start Master " + rank + " --> OK");		
	}
	
	public void startSlaves(int rank){
		runCommand(HADOOP_FOLDER + "/sbin/hadoop-daemon.sh start datanode");
		System.out.println("Start Slave " + rank + " --> OK");	
	}	
	
	public void spawnOnSlaves(int rank, int numberSlaves){
		try {
			String commands[] = new String[numberSlaves];
			for (int i = 0; i < numberSlaves; i++) {
				commands[i] = "nodemanager.sh";
			}

			String params[][] = new String[numberSlaves][];
			for (int i = 0; i < numberSlaves; i++) {
				params[i] = new String[2];
				params[i][0] = "start";
				params[i][1] = "nodemanager";				
			}

			int procs[] = new int[numberSlaves];
			for (int i = 0; i < numberSlaves; i++) {
				procs[i] = 1;
			}			
			
			Info infos[] = new Info[numberSlaves];
			for (int i = 0; i < numberSlaves; i++) {
				infos[i] = new Info();
				infos[i].set("host", "slave" + (i + 1));
			}

			for (int i = 0; i < numberSlaves; i++) {
				int error[] = new int[procs[i]];
				spawn[i] = MPI.COMM_WORLD.spawn(commands[i], params[i], procs[i], infos[i], 0, error);
				if (rank == 0) {
					for (int j = 0; j < procs[i]; j++) {
						if (error[j] == MPI.SUCCESS) {
							System.out.println("Spawn " + i + " OK");
						}
					}
				}				
			}
		} catch (MPIException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void receiveSpawnFromChild(Intercomm group, int child, String host){
		try {
			char[] message = new char[500]; 
			group.recv(message, 500, MPI.CHAR, child, Constants.TAG);
			String cmd = String.valueOf(message).trim();
			
			System.out.println(host + " start spawning Grand Child");
			spawnGrandChild(cmd, group, child, host);
			
			// Spawn
		} catch (MPIException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}				
	}
	
	public void spawnGrandChild(String cmd, Intercomm group, int child, String host){
		try {
			String params[] = {};
			int proc = 1;
			Info info = new Info();
			info.set("host", host);
			int error[] = new int[proc];
			Intercomm spawnChild = MPI.COMM_WORLD.spawn(cmd, params, proc, info, 0, error);
			if (error[0] == MPI.SUCCESS) {
				System.out.println("Grand child " + host + " Spawned " + " OK");
				CharBuffer message = ByteBuffer.allocateDirect(500).asCharBuffer();
				Request request = spawnChild.iRecv(message, 500, MPI.CHAR, 0, Constants.TAG);
				mrMessage.add(message);
				mrRequest.add(request);
				mrSpawn.add(spawnChild);
			}
		} catch (MPIException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}		
	
	/**
	 * Call bash command
	 * @param command
	 */
	public static void runCommand(String command){		
		ProcessBuilder processBuilder = new ProcessBuilder("/bin/sh", "-c", command);
		Process process;
		try {
			process = processBuilder.start();
			InputStream stderr = process.getErrorStream();
			InputStreamReader isr = new InputStreamReader(stderr);
			BufferedReader br = new BufferedReader(isr);
			String line;
			while ((line = br.readLine()) != null){
				System.out.println(line);
			}
			process.waitFor();			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}			
	
	public static void main(String args[]) throws MPIException{
		MPI.Init(args);
		new Hadoop();
		MPI.Finalize();
	}
}
