package de.zabuza.treeflood.exploration.localstorage;

/**
 * Container holding information of a robot entering a node.
 * 
 * @author Zabuza {@literal <zabuza.dev@gmail.com>}
 *
 */
public final class Information {
	/**
	 * A special port that can be used to indicate that a robot moves along the
	 * parent port.
	 */
	public static final int PARENT_PORT = -2;
	/**
	 * A special port that indicates that a robot was initially placed at the
	 * current node and did not use any port to move there, also known as *-port
	 * (star-port).
	 */
	public static final int STAR_PORT = -1;
	/**
	 * A special port indicating that a robot did not move in the last move
	 * stage.
	 */
	public static final int STAY_PORT = -3;
	/**
	 * Whether the robot entered the node through its parent or from a child.
	 */
	private final boolean mFromParent;
	/**
	 * The number of the port the robot entered a node in the given step.
	 */
	private final int mPort;
	/**
	 * The unique id of the robot this information is of.
	 */
	private final int mRobotId;
	/**
	 * The number of the step this information is of.
	 */
	private final int mStep;

	/**
	 * Creates a new information container holding the data of a robot that
	 * entered a node with the given port in the given step.
	 * 
	 * @param step
	 *            The number of the step this information is of
	 * @param robotId
	 *            The unique id of the robot this information is of
	 * @param port
	 *            The number of the port the robot entered the node in the given
	 *            step
	 * @param fromParent
	 *            <tt>True</tt> if the robot entered the node through its
	 *            parent, <tt>false</tt> if he entered from a child
	 */
	public Information(final int step, final int robotId, final int port, final boolean fromParent) {
		this.mStep = step;
		this.mRobotId = robotId;
		this.mPort = port;
		this.mFromParent = fromParent;
	}

	/**
	 * Gets the number of the port the robot entered the node in the given step.
	 * 
	 * @return The number of the port the robot entered the node in the given
	 *         step
	 */
	public int getPort() {
		return this.mPort;
	}

	/**
	 * Gets the unique id of the robot this information is of
	 * 
	 * @return The unique id of the robot this information is of
	 */
	public int getRobotId() {
		return this.mRobotId;
	}

	/**
	 * Gets the number of the step this information is of
	 * 
	 * @return The number of the step this information is of
	 */
	public int getStep() {
		return this.mStep;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "Information [step=" + this.mStep + ", robotId=" + this.mRobotId + ", port=" + this.mPort
				+ ", fromParent=" + this.mFromParent + "]";
	}

	/**
	 * Whether the robot entered the node through its parent or from a child.
	 * 
	 * @return <tt>True</tt> if the robot entered the node through its parent,
	 *         <tt>false</tt> if he entered from a child
	 */
	public boolean wasEnteredFromParent() {
		return this.mFromParent;
	}

}
