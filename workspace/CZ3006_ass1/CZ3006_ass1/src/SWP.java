/*===============================================================================*   
 *  Author:			ALVIN LEE YONG TECK		                    				 *
 *  Matric No:      U1620768F				                    				 *
 *  Tutorial Group: SSP4				                         				 *
 *  Lab Assignment: Assignment 1 - Implementation of a Sliding Window Protocol   *
 *  Course Code:	CZ3006 NET CENTRIC COMPUTING               				     *
 *===============================================================================*/

/*===============================================================*
 *  File: SWP.java                                               *
 *                                                               *
 *  This class implements the sliding window protocol            *
 *  Used by VMach class					         *
 *  Uses the following classes: SWE, Packet, PFrame, PEvent,     *
 *                                                               *
 *  Author: Professor SUN Chengzheng                             *
 *          School of Computer Engineering                       *
 *          Nanyang Technological University                     *
 *          Singapore 639798                                     *
 *===============================================================*/

import java.util.*;

public class SWP {

	/*
	 * ========================================================================*
	 * the following are provided, do not change them!!
	 * ========================================================================
	 */
	// the following are protocol constants.
	public static final int MAX_SEQ = 7;
	public static final int NR_BUFS = (MAX_SEQ + 1) / 2;

	// the following are protocol variables
	private int oldest_frame = 0;
	private PEvent event = new PEvent();
	private Packet out_buf[] = new Packet[NR_BUFS];

	// the following are used for simulation purpose only
	private SWE swe = null;
	private String sid = null;

	// Constructor
	public SWP(SWE sw, String s) {
		swe = sw;
		sid = s;
	}

	// the following methods are all protocol related
	private void init() {
		for (int i = 0; i < NR_BUFS; i++) {
			out_buf[i] = new Packet();
		}
	}

	private void wait_for_event(PEvent e) {
		swe.wait_for_event(e); // may be blocked
		oldest_frame = e.seq; // set timeout frame seq
	}

	private void enable_network_layer(int nr_of_bufs) {
		// network layer is permitted to send if credit is available
		swe.grant_credit(nr_of_bufs);
	}

	private void from_network_layer(Packet p) {
		swe.from_network_layer(p);
	}

	private void to_network_layer(Packet packet) {
		swe.to_network_layer(packet);
	}

	private void to_physical_layer(PFrame fm) {
		System.out.println("SWP: Sending frame: seq = " + fm.seq + " ack = "
				+ fm.ack + " kind = " + PFrame.KIND[fm.kind] + " info = "
				+ fm.info.data);
		System.out.flush();
		swe.to_physical_layer(fm);
	}

	private void from_physical_layer(PFrame fm) {
		PFrame fm1 = swe.from_physical_layer();
		fm.kind = fm1.kind;
		fm.seq = fm1.seq;
		fm.ack = fm1.ack;
		fm.info = fm1.info;
	}

	/* ==========================================================================
	 * implement your Protocol Variables and Methods below:
	 * ========================================================================== */

	// No nak has been sent yet
	private boolean no_nak = true;	
	
	// To check if the frame number is in the window (e.g. circular window range)
	private static boolean between(int a, int b, int c)
	{
		// Same as between in protocol5, but shorter and more obscure
		return ((a <= b) && (b < c)) || ((c < a) && (a <= b)) || ((b < c) && (c < a)); // the logic to check for Circular Window Range
	}
	
	// Function is separated to re-invoke in other parts of the code
	private void send_frame(int frame_kind, int frame_nr, int frame_expected, Packet buffer[])
	{
		// Construct and send a data, ack, or nak frame
		PFrame s = new PFrame(); 					// create a new frame for the sender to send
		s.kind = frame_kind; 						// frame_kind can be equal to data, ack, or nak
		
		// If the frame_kind is data, take data from one of the buffer
		if (frame_kind == PFrame.DATA) {
			s.info = buffer[frame_nr % NR_BUFS];
		}
		s.seq = frame_nr; 							// only meaningful for data frames
		s.ack = ((frame_expected + MAX_SEQ) % (MAX_SEQ + 1));
		
		// If the frame_kind is nak, i.e. nak has been sent out (one nak per frame)
		if (frame_kind == PFrame.NAK) {
			no_nak = false; 						// one nak per frame, please
		}
		
		to_physical_layer(s); 						// sending the frame to receiver
		
		if (frame_kind == PFrame.DATA) {
			start_timer(frame_nr); 					// start timer for sending of data
		}
		
		stop_ack_timer(); 							// no need for separate ack frame
	}
	
	// To increase the frame number in a circular manner
	private static int inc(int num){
		num = ((num + 1) % (MAX_SEQ + 1));
		return num;
	}
	
	public void protocol6() {
		init();
		
		int ack_expected = 0;						// lower edge of sender’s window
													// next ack expected on the inbound stream
		
		int next_frame_to_send = 0;					// upper edge of sender’s window + 1
													// number of next outgoing frame
		
		int frame_expected = 0;						// lower edge of receiver’s window
		int too_far = NR_BUFS;						// upper edge of receiver’s window + 1
		int i; 										// index into buffer pool
		
		PFrame r = new PFrame(); 					// scratch variable
		//Packet out_buf[] = new Packet[NR_BUFS]; 	// buffers for the outbound stream
		Packet in_buf[] = new Packet[NR_BUFS]; 		// buffers for the inbound stream
		boolean arrived[] = new boolean[NR_BUFS]; 	// inbound bit map 
		
		//int nbuffered = 0;							// how many output buffers currently used
													// initially no packets are buffered
		
		enable_network_layer(NR_BUFS); 				// initialize
		
		for (i = 0; i < NR_BUFS; i++) {
			arrived[i] = false;
		}

		while (true) {
			wait_for_event(event);					// five possibilities: see event.type above
			switch (event.type) {
			/* ======================================
			 * PEvent.NETWORK_LAYER_READY
			 * - Network layer have a packet to send
			 * ======================================
			 */
			case (PEvent.NETWORK_LAYER_READY):											// accept, save, and transmit a new frame
				//nbuffered = nbuffered + 1; 												// expand the window
				from_network_layer(out_buf[next_frame_to_send % NR_BUFS]); 				// fetch new packet
				send_frame(PFrame.DATA, next_frame_to_send, frame_expected, out_buf); 	// transmit the frame
				next_frame_to_send = inc(next_frame_to_send); 							// advance upper window edge
				break;
				
			/* ======================================
			 * PEvent.FRAME_ARRIVAL
			 * - A data or control frame has arrived
			 * ======================================
			 */
			case (PEvent.FRAME_ARRIVAL):
				from_physical_layer(r); 																		// fetch incoming frame from physical layer
				// check if frame kind is data
				if (r.kind == PFrame.DATA) { 																	// An undamaged frame has arrived
					if ((r.seq != frame_expected) && no_nak) {
						send_frame(PFrame.NAK, 0, frame_expected, out_buf);  									// frame arrives out of order - send NAK
					}
					else {
						start_ack_timer(); 																		// see if separate ack is needed or not
					}
					if (between(frame_expected, r.seq, too_far) && (arrived[r.seq % NR_BUFS] == false)) { 		// buffer not occupied
						// Frames may be accepted in any order
						arrived[r.seq % NR_BUFS] = true; 														// mark buffer as full
						in_buf[r.seq % NR_BUFS] = r.info; 														// insert data into buffer
						while (arrived[frame_expected % NR_BUFS]) {												// check if buffered frames are in-order
							// Pass frames and advance window
							to_network_layer(in_buf[frame_expected % NR_BUFS]);									// pass to network layer if in-order
							no_nak = true;																		// no nak has been sent
							arrived[frame_expected % NR_BUFS] = false;
							frame_expected = inc(frame_expected); 												// advance lower edge of receiver’s window
							too_far = inc(too_far); 															// advance upper edge of receiver’s window
							start_ack_timer(); 																	// see if a separate ack is needed or not
						}
					}
				}
				
				if((r.kind == PFrame.NAK) && between(ack_expected,(r.ack+1)%(MAX_SEQ+1),next_frame_to_send)){
					send_frame(PFrame.DATA, (r.ack+1) % (MAX_SEQ + 1), frame_expected, out_buf);
				}
				
				while (between(ack_expected, r.ack, next_frame_to_send)) {
					//nbuffered = nbuffered - 1; 					                // handle piggybacked ack
					stop_timer(ack_expected % NR_BUFS); 							// frame arrived intact
					ack_expected = inc(ack_expected); 								// advance lower edge of sender’s window
					enable_network_layer(1);
				}
				break;
				
			/* ======================================
			 * PEvent.CKSUM_ERR
			 * ======================================
			 */
			case (PEvent.CKSUM_ERR):
				if (no_nak) {
					send_frame(PFrame.NAK, 0, frame_expected, out_buf); 			// damaged frame (checking if NAK sent)
				}
				break;
				
			/* ======================================
			 * PEvent.TIMEOUT
			 * ======================================
			 */
			case (PEvent.TIMEOUT):
				send_frame(PFrame.DATA, oldest_frame, frame_expected, out_buf); 	// timed out
				break;
			
			/* ======================================
			 * PEvent.ACK_TIMEOUT
			 * ======================================
			 */
			case (PEvent.ACK_TIMEOUT):
				send_frame(PFrame.ACK,0,frame_expected, out_buf); 					// ack timer expired, send a separate ack
				break;
			default:
				System.out.println("SWP: undefined event type = " + event.type);
				System.out.flush();
			}
		}
	}

	/*
	 * Note: when start_timer() and stop_timer() are called, the "seq" parameter
	 * must be the sequence number, rather than the index of the timer array, of
	 * the frame associated with this timer,
	 */
	
	private Timer[] clk_timer = new Timer[NR_BUFS];
	private Timer ack_timer = new Timer();
	private static final int ack_delay = 250; 		// delay for ACK waiting for outgoing frame
	private static final int delay = 500;			// delay for ACK

	private void start_timer(int seq) {
		stop_timer(seq);
		clk_timer[seq % NR_BUFS] = new Timer();
		clk_timer[seq % NR_BUFS].schedule(new TempTimerTask(seq), delay);
	}

	private void stop_timer(int seq) {
		try{
			clk_timer[seq % NR_BUFS].cancel();
		}
		catch (Exception e){
			
		}
	}

	private void start_ack_timer() {
		stop_ack_timer();
		ack_timer = new Timer();
		ack_timer.schedule(new TimerTask() {
			public void run() {
				swe.generate_acktimeout_event();
			}
		}, ack_delay);
	}

	private void stop_ack_timer() {
		try {
			ack_timer.cancel();
		}
		catch (Exception e){
			
		}
	}
	
	private class TempTimerTask extends TimerTask{

		public int seq;								// keeps track of sequence number
		public TempTimerTask (int seq){
			super();
			this.seq = seq;
		}
		public void run() {
			swe.generate_timeout_event(this.seq);	// timeout event
		}
		
	}

}// End of class

/*
 * Note: In class SWE, the following two public methods are available: .
 * generate_acktimeout_event() and . generate_timeout_event(seqnr).
 * 
 * To call these two methods (for implementing timers), the "swe" object should
 * be referred as follows: swe.generate_acktimeout_event(), or
 * swe.generate_timeout_event(seqnr).
 */

