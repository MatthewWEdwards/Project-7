/* Chat Room ChatServer.java
 * EE422C Project 7 submission by
 * Regan Stehle
 * rms3762
 * 16465
 * Matthew Edwards
 * mwe295
 * 16475
 * Slip days used: <0>
 * Fall 2016
 */

package assignment7;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Scanner;

public class ChatServer {
	private ArrayList<ChatUser> userList = new ArrayList<ChatUser>();
	private ArrayList<ChatRoom> roomList = new ArrayList<ChatRoom>();
	private HashMap<String, ArrayList<ChatUser>> friendList = new HashMap<String, ArrayList<ChatUser>>();
	protected static HashMap<String, ArrayList<String>> friendRequests = new HashMap<String, ArrayList<String>>();

	public static void main(String[] args) {
		try {
			new ChatServer().setUpNetworking();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void setUpNetworking() throws Exception {
		@SuppressWarnings("resource")
		ServerSocket serverSock = new ServerSocket(4242);
		while (true) {
			Socket clientSocket = serverSock.accept();
			Thread t = new Thread(new ClientHandler(clientSocket));
			t.start();
			System.out.println("got a connection");
		}

	}

	private void notifyClients(String message, Socket sock) {
		String.valueOf(sock.getPort());
		for (ChatUser u : userList) {
			if (u.getOnlineStatus()) {
				u.getWriter().println(String.valueOf(sock.getPort()) + " - " + message);
				u.getWriter().flush();
			}
		}
	}
	
	private void updateUserList(){
		String message = new String();
		message = "~updateUsers ";
		for (ChatUser u : userList) {
			if (u.getOnlineStatus()) {
				message += u.toString() + " ";
			}
		}
		for (ChatUser u : userList) {
			if (u.getOnlineStatus()) {
				u.getWriter().println(message);
				u.getWriter().flush();
			}
		}
	}
	
	private void updateRoomList(){
		String message = new String();
		message = "~updateRooms ";
		for (ChatRoom r : roomList) {
			message += r.toString() + " ";
		}
		for (ChatUser u : userList) {
			if (u.getOnlineStatus()) {
				u.getWriter().println(message);
				u.getWriter().flush();
			}
		}
	}

	class ClientHandler implements Runnable {
		private BufferedReader reader;
		private Socket sock;
		private PrintWriter sockWriter;

		public ClientHandler(Socket clientSocket) throws IOException {
			sock = clientSocket;
			reader = new BufferedReader(new InputStreamReader(sock.getInputStream()));
			sockWriter = new PrintWriter(sock.getOutputStream());
		}

		public void run() {
			String message;
			try {
				while ((message = reader.readLine()) != null) {
					if (message.length() > 0 && message.charAt(0) == ApprovedChars.signalingChar.charAt(0)) {
						command(message);
					} else {
						System.out.println("read " + message);
						notifyClients(message, sock);
					}
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		/**
		 * execute commands sent by the client to the server
		 * 
		 * @param message:
		 *            The message sent by the client to the server.
		 */
		public void command(String message) {
			String username = new String();
			String password = new String();
			switch (message.substring(1, message.indexOf(" "))) {

			// Login to the chat server, or tell the user the username
			// doesn't exist, or is already logged on
			case "login":
				username = message.substring(message.indexOf(" ") + 1, message.lastIndexOf(" "));
				password = message.substring(message.lastIndexOf(" ") + 1, message.length());
				for (ChatUser u : userList) {
					if (u.getUsername().equals(username)) {
						if(!(u.getPassword().equals(password))){
							sockWriter.println(ApprovedChars.signalingChar + "passwordMismatch ");
							sockWriter.flush();
							return;
						}
						if (u.getOnlineStatus()) {
							sockWriter.println(ApprovedChars.signalingChar + "alreadyOnline ");
							sockWriter.flush();
							return;
						} else {
							u.setWriter(sockWriter);
							u.setPort(sock.getPort());
							u.setOnlineStatus(true);
							u.getWriter().println(ApprovedChars.signalingChar + "login " + username);
							u.getWriter().flush();
							updateUserList();
							return;
						}
					}
				}
				sockWriter.println(ApprovedChars.signalingChar + "noUser ");
				sockWriter.flush();
				break;

				
			case "logout":
				username = message.substring(message.indexOf(" ") + 1);
				for (ChatUser u : userList) {
					if (u.getUsername().equals(username)) {
						u.setOnlineStatus(false);
						updateUserList();
						return;
					}
				}
				
				break;
			// Create a new user, or tell the client a username is taken
			case "register":
				username = message.substring(message.indexOf(" ") + 1, message.lastIndexOf(" "));
				password = message.substring(message.lastIndexOf(" ") + 1, message.length());
				ChatUser toAdd = new ChatUser(username, password);
				for (ChatUser u : userList) {
					if (u.equals(toAdd)) {
						sockWriter.println(ApprovedChars.signalingChar + "registerError ");
						sockWriter.flush();
						return;
					}
				}
				userList.add(toAdd);
				friendList.put(toAdd.getUsername(), new ArrayList<ChatUser>());
				friendRequests.put(toAdd.getUsername(), new ArrayList<String>());
				break;
				
			case "request":
				username = message.substring(message.indexOf(" ") + 1, message.lastIndexOf(" "));
				password = message.substring(message.lastIndexOf(" ") + 1, message.length()); //password is actually current user
				boolean exist = false;
				if(username.equals(password)){
					//same person
					sockWriter.println(ApprovedChars.signalingChar + "sameUser ");
					sockWriter.flush();
					return;
				}
					
				for (ChatUser u : userList) {
					if (u.getUsername().equals(username)) {
						exist = true;
						boolean isFriend = false;
						//check if they're already friends, then either request sent or already friends
						ArrayList<ChatUser> friends = friendList.get(username);
						for(int i = 0; i < friends.size(); i++){
							ChatUser f = friends.get(i);
							if (f.getUsername().equals(password)){ //where password = current user
								isFriend = true;
							}
						}
						if(isFriend){ 
							sockWriter.println(ApprovedChars.signalingChar + "alreadyFriends ");
							sockWriter.flush();
							return;
						}
						else {
							ArrayList<String> requests =  friendRequests.get(username);
							requests.add(password); //where password = current user
							friendRequests.put(username, requests);
							sockWriter.println(ApprovedChars.signalingChar + "requestSent ");
							sockWriter.flush();
							return;
						}
					}
					
				}
				if(!exist){
					sockWriter.println(ApprovedChars.signalingChar + "noUser ");
					sockWriter.flush();
					return;
				}
					
				break;
			case "makeChatRoom":

				String chatRoom = message.substring(message.indexOf(" ") + 1);
				Scanner s = new Scanner(chatRoom);
				String chatRoomName = s.next();
				String chatRoomowner = s.next();
				boolean chatRoomPublicPrivate = Boolean.valueOf(s.next());
				ChatRoom newChatRoom = new ChatRoom(chatRoomName, chatRoomowner, chatRoomPublicPrivate);
				for (ChatRoom r : roomList) {
					if (r.equals(newChatRoom)) {
						//TODO: room already exists
						return;
					}
				}
				roomList.add(newChatRoom);
				s.close();
				updateRoomList();
				break;

			// Do nothing
			default:
				return;
			}
		}
	}

}
