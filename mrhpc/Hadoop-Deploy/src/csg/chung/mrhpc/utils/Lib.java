package csg.chung.mrhpc.utils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.DataInputBuffer;
import org.apache.hadoop.io.WritableUtils;

import mpi.MPI;
import mpi.MPIException;
import mpi.Status;
import csg.chung.mrhpc.processpool.Configure;
import csg.chung.mrhpc.processpool.ReadIndex;
import csg.chung.mrhpc.processpool.SendingPool;
import csg.chung.mrhpc.processpool.ShuffleManager;
import csg.chung.mrhpc.utils.Constants;

public class Lib {
	public static String MAP_OUTPUT_DATA = "/group/gc83/c83014/hadoop-mpi-inmemory/deploy/mapoutput.txt";
	public static String MAP_OUTPUT_DATA_ORI = "/group/gc83/c83014/hadoop-mpi-inmemory/deploy/mapoutputOri.txt";	
	public static ByteBuffer bufData = ByteBuffer.allocateDirect(SendingPool.SLOT_BUFFER_SIZE);
	public static int bufDataLength = 0;
	public static final int EOF_MARKER = -1; // End of File Marker
	
	public static void main(String[] args) throws UnsupportedEncodingException{
		byte[] data = {11, 12, 13};
		String mapID = "chung";
		int rID = 7;
		String header = Constants.SPLIT_REGEX_HEADER_DATA + mapID + Constants.SPLIT_REGEX + rID;
		System.out.println(getStringLengthInByte(header));
		
		ByteBuffer buf = putHeaderAndDataToBuf(mapID, rID, data);
		MapOutputObj obj = readDataFromBuffer(buf, 31);
		obj.getData().position(0);
		obj.getData().get();
		System.out.println(obj.getData().get());
		
		Path path = new Path("/home/chung/test.txt");
		System.out.println(path.toString());
	}
	
	public static List<IndexFileObj> getIndexList(String indexPath, String mapID, int numberReducers) throws IOException{
		List<IndexFileObj> list = new ArrayList<IndexFileObj>();
		
		for (int i=0; i < numberReducers; i++){
			ReadIndex info = new ReadIndex(indexPath, i);
			long length = info.getLength();
			long start = info.getStart();
			
			IndexFileObj obj = new IndexFileObj(mapID, i, length, start);
			list.add(obj);
		}
		
		return list;
	}
	
	public static int getExtraNodeMgrRank() throws MPIException{
		int rank = MPI.COMM_WORLD.getSize() - 1;
		
		return rank;
	}
	
	public static int getShuffleServerRank() throws MPIException{
		return (MPI.COMM_WORLD.getRank() / Configure.NUMBER_PROCESS_EACH_NODE) * Configure.NUMBER_PROCESS_EACH_NODE + 1;
	}
	
	public static void sendMsgToServer(String msg, int server) throws UnsupportedEncodingException, MPIException{
		ByteBuffer bufSend = ByteBuffer.allocateDirect(ShuffleManager.RECV_BUFFER_CAPACITY);
		bufSend.position(0);
		Lib.putString(bufSend, msg);		
		MPI.COMM_WORLD.send(bufSend, Lib.getStringLengthInByte(msg), MPI.BYTE, server, Constants.EXCHANGE_MSG_TAG);						
	}
	
	public static int checkSendMapOutput(int server) throws MPIException, UnsupportedEncodingException{
		String msg = Constants.CMD_CHECK_SPACE;
		ByteBuffer bufSend = ByteBuffer.allocateDirect(ShuffleManager.RECV_BUFFER_CAPACITY);
		bufSend.position(0);
		Lib.putString(bufSend, msg);
		MPI.COMM_WORLD.send(bufSend, Lib.getStringLengthInByte(msg), MPI.BYTE, server, Constants.EXCHANGE_MSG_TAG);				
		
		ByteBuffer bufRecv = ByteBuffer.allocateDirect(ShuffleManager.RECV_BUFFER_CAPACITY);
		Status status = MPI.COMM_WORLD.recv(bufRecv, bufRecv.capacity(), MPI.BYTE, server, Constants.EXCHANGE_MSG_TAG);
		
		String cmd = Lib.getStringByNumberOfCharacters(bufRecv,
				status.getCount(MPI.BYTE) / Lib.getUTF_16_Character_Size());

		String split[] = cmd.split(Constants.SPLIT_REGEX);
		return Integer.parseInt(split[1]);
	}
	
	public static void sendMapOutputToShuffleServer(String mapID, int rID, ByteBuffer buf, int length, int serverRank) throws MPIException, UnsupportedEncodingException{
		String header = Constants.SPLIT_REGEX_HEADER_DATA + mapID + Constants.SPLIT_REGEX + rID;
		buf.position(length);
		putString(buf, header);
		
		// Send to shuffle engine
		length += getStringLengthInByte(header);
		MPI.COMM_WORLD.send(buf, length, MPI.BYTE, serverRank, Constants.EXCHANGE_MSG_TAG);
	}
	
	public static void sendMapOutputToShuffleServer(List<IndexFileObj> list, String path) throws IOException, MPIException{
		RandomAccessFile file = new RandomAccessFile(path, "r");
		
		for (int i=0; i < list.size(); i++){
			byte[] data = new byte[(int) list.get(i).getLength()];
			file.seek(list.get(i).getStart());
			file.read(data);
			
			// Send to shuffle engine
			int shuffleRank = (MPI.COMM_WORLD.getRank() / Configure.NUMBER_PROCESS_EACH_NODE) * Configure.NUMBER_PROCESS_EACH_NODE + 1; 
			ByteBuffer buf = putHeaderAndDataToBuf(list.get(i).getMapID(), list.get(i).getRID(), data);
			int length = getStringLengthInByte(Constants.SPLIT_REGEX_HEADER_DATA + list.get(i).getMapID() + Constants.SPLIT_REGEX + list.get(i).getRID());
			length += list.get(i).length;
			
			//System.out.println("Send: " + length);
			//MPI.COMM_WORLD.iSend(buf, (int) length, MPI.BYTE, shuffleRank, Constants.EXCHANGE_MSG_TAG);
			MPI.COMM_WORLD.send(buf, (int) length, MPI.BYTE, shuffleRank, Constants.EXCHANGE_MSG_TAG);
		}
		
		file.close();
	}
	
	public static ByteBuffer putHeaderAndDataToBuf(String mapID, int rID, byte[] data){
		ByteBuffer buf = ByteBuffer.allocateDirect(SendingPool.SLOT_BUFFER_SIZE);
		buf.position(0);
		buf.put(data);

		String header = Constants.SPLIT_REGEX_HEADER_DATA + mapID + Constants.SPLIT_REGEX + rID;
		putString(buf, header);	
		
		return buf;
	}
	
	public static MapOutputObj storeExtraNodeInfo(String mapID, int rID, int extraNodeRank) throws UnsupportedEncodingException{
		ByteBuffer data = ByteBuffer.allocateDirect(ShuffleManager.RECV_BUFFER_CAPACITY);
		data.position(0);
		
		String msg = Constants.CMD_NOTIFY_EXTRA_NODE + Constants.SPLIT_REGEX + extraNodeRank;
		Lib.putString(data, msg);
		
		MapOutputObj obj = new MapOutputObj(mapID, rID, data, Lib.getStringLengthInByte(msg));
		return obj;
	}
	
	public static MapOutputObj readDataFromBuffer(ByteBuffer buf, int length) throws UnsupportedEncodingException{
		int splitRegexCount = 0;
		String header = "";
		
		for (int i=0; i < Constants.HEADER_MAX_LENGTH; i++){
			buf.position(length - 2*(i+1));			
			char c = buf.getChar();
			header = c + header;
			if (c == '@'){
				splitRegexCount++;
			}else{
				splitRegexCount = 0;
			}
			
			if (splitRegexCount == Constants.SPLIT_REGEX_HEADER_DATA.length()){
				String[] split = header.substring(Constants.SPLIT_REGEX_HEADER_DATA.length(), header.length()).split(Constants.SPLIT_REGEX);
				String mapID = split[0];
				int rID = Integer.parseInt(split[1]);
				
				ByteBuffer data = ByteBuffer.allocateDirect(SendingPool.SLOT_BUFFER_SIZE);
				buf.position(0);
				data.position(0);
				data.put(buf);
				
				MapOutputObj obj = new MapOutputObj(mapID, rID, data, length - getStringLengthInByte(header));
				return obj;
			}
		}
		
		return null;
	}
	
	public static void writeToFile(String path, ByteBuffer buf, int length) throws IOException{
		FileOutputStream out = new FileOutputStream(path, true);
		out.write(Lib.getByteFromByteBuffer(buf, length));
		out.close();
	}

	public static void resetBuffer(ByteBuffer buf){
		buf.clear();
		bufDataLength = 0;
	}
	
	public static void closeBuffer(ByteBuffer buf) throws IOException{
		writeEOF(buf);
	}	
	
	public static void writeKeyAndValueToByteBuffer(ByteBuffer buf, DataInputBuffer key, DataInputBuffer value) throws IOException{
		int keyLength = key.getLength() - key.getPosition();
		int valueLength = value.getLength() - value.getPosition();

		writeVInt(buf, keyLength);
		writeVInt(buf, valueLength);
		buf.put(key.getData(), key.getPosition(), keyLength);
		buf.put(value.getData(), value.getPosition(), valueLength);
		
		bufDataLength += keyLength + valueLength + 
                WritableUtils.getVIntSize(keyLength) + 
                WritableUtils.getVIntSize(valueLength);
	}
	
	public static void writeEOF(ByteBuffer buf) throws IOException{
		writeVInt(buf, EOF_MARKER);
		writeVInt(buf, EOF_MARKER);
		bufDataLength += 2 * WritableUtils.getVIntSize(EOF_MARKER);
	}
	
	public static void writeVInt(ByteBuffer buf, long i) throws IOException {
		writeVLong(buf, i);
	}
	
	public static void writeVLong(ByteBuffer buf, long i) throws IOException {
		if (i >= -112 && i <= 127) {
			buf.put((byte) i);
			return;
		}

		int len = -112;
		if (i < 0) {
			i ^= -1L; // take one's complement'
			len = -120;
		}

		long tmp = i;
		while (tmp != 0) {
			tmp = tmp >> 8;
			len--;
		}

		buf.put((byte) len);

		len = (len < -120) ? -(len + 120) : -(len + 112);

		for (int idx = len; idx != 0; idx--) {
			int shiftbits = (idx - 1) * 8;
			long mask = 0xFFL << shiftbits;
			buf.put((byte) ((i & mask) >> shiftbits));
		}
	}
	
	public static void writeToBuffer(ByteBuffer buf, byte[] data, int off, int len){
		buf.put(data, off, len);
	}

	public static void writeIntToBuffer(ByteBuffer buf, int data){
		buf.putInt(data);
	}
	
	public static byte[] getByteFromByteBuffer(ByteBuffer data, int length){
		byte[] result = new byte[length];
		
		data.position(0);
		for (int i=0; i < length; i++){
			result[i] = data.get();
		}
		
		return result;
	}
	
	public static void printNodeInfo(int rank, int size){
		try {
			InetAddress ip = InetAddress.getLocalHost();
			long memory = Runtime.getRuntime().maxMemory();
			System.out.println("P" + rank + "/" + size + ": " + ip.getHostName() + " - " + ip.getHostAddress() + " --> memory: " + memory);						
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public static void printNodeInfo(int rank, int size, String prefix){
		try {
			InetAddress ip = InetAddress.getLocalHost();
			long memory = Runtime.getRuntime().maxMemory();
			System.out.println(prefix + " P" + rank + "/" + size + ": " + ip.getHostName() + " - " + ip.getHostAddress() + " --> memory: " + memory);						
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}	
	
	public static String getHostname(){
		try {
			InetAddress ip = InetAddress.getLocalHost();
			return ip.getHostName();
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}		
		
		return null;
	}
	
	public static String getHostAddress(){
		try {
			InetAddress ip = InetAddress.getLocalHost();
			return ip.getHostAddress();
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}		
		
		return null;
	}		
	
	public static int getRankFromHost(String hostfile, String host){
		int rank = Constants.UNKNOW_INT;
		
		try {
			FileReader fr = new FileReader(new File(hostfile));
			BufferedReader in = new BufferedReader(fr);
			
			String line;
			while ((line = in.readLine()) != null){
				String split[] = line.split(Constants.SPLIT_REGEX);
				if (split[1].equals(host)){
					rank = Integer.parseInt(split[0]);
				}
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
		
		return rank;
	}	
	
	public static int getRank(){
		try {
			return MPI.COMM_WORLD.getRank();
		} catch (MPIException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return -1;
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
	
	/**
	 * Call bash command
	 * @param command
	 */
	public static void runCommand(String command, String home){		
		ProcessBuilder processBuilder = new ProcessBuilder("/bin/sh", "-c", command);
		processBuilder.directory(new File(home));
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
	
	public static String buildCommand(String... args){
		String result = "";
		for (int i=0; i < args.length; i++){
			if (i == args.length - 1){
				result = result + args[i];
			}else{
				result = result + args[i] + Constants.SPLIT_REGEX;
			}
		}
		return result;
	}	
	
	public static void appendToFile(String filename, String data){
		try{
	   		File file =new File(filename);
	
			if(!file.exists()){
				file.createNewFile();
			}
	
			FileWriter fileWritter = new FileWriter(filename,true);
			BufferedWriter bufferWritter = new BufferedWriter(fileWritter);
			
			bufferWritter.write(data + "\n");
			
			bufferWritter.close();		
			fileWritter.close();
		}catch(IOException ex){
			
		}
	}
	
	public static byte[] readFile(String path, long start, long length) throws IOException{
		RandomAccessFile file = new RandomAccessFile(path, "r");
		byte[] data = new byte[(int) length];
		file.seek(start);
		file.read(data);
		file.close();
		
		return data;
	}		
	
	public static String getString(ByteBuffer b) {
		String data = "";
		char c;
		b.position(0);
		while ((c=b.getChar()) != '\0'){
			data += c;
		}
		
		return data;
	}	
	
	public static ByteBuffer putString(ByteBuffer b, String msg){
		for (int i=0; i < msg.length(); i++){
			b.putChar(msg.charAt(i));
		}
		
		return b;
	}
	
	public static String getStringByNumberOfCharacters(ByteBuffer b, int number) {
		b.position(0);
		String data = "";
		
		for (int i=0; i < number; i++){
			data += b.getChar();
		}
		
		return data;
	}
	
	public static boolean checkStringEqual(String a, String b){
		a = a.trim();
		b = b.trim();
		
		if (a.contains(b) || b.contains(a) || a.equals(b)){
			return true;
		}
		
		return false;
	}
	
	public static int getStringLengthInByte(String s) throws UnsupportedEncodingException{
		return s.getBytes("UTF-16").length - 2;
	}
	
	public static int getUTF_16_Character_Size(){
		return 2;
	}
}
