package de.zabuza.treeflood.exploration.localstorage;

import java.util.List;

import de.zabuza.treeflood.exploration.localstorage.listener.IRobotEncounteredExceptionListener;
import de.zabuza.treeflood.exploration.localstorage.listener.IRobotMovedListener;
import de.zabuza.treeflood.exploration.localstorage.storage.ILocalStorage;
import de.zabuza.treeflood.tree.ITreeNode;
import de.zabuza.treeflood.util.NestedMap2;

/**
 * A robot that can explores a tree. All calls to the robot should be done via
 * threads in order to let robots run distributedly. Robots can only communicate
 * over the local storage nodes. Thus they do not have a remove communication
 * channel.
 * 
 * @author Zabuza {@literal <zabuza.dev@gmail.com>}
 *
 */
public final class Robot implements Comparable<Robot> {
	/**
	 * The current node the robot is in.
	 */
	private ITreeNode mCurrentNode;
	/**
	 * The current stage the robot is in.
	 */
	private EStage mCurrentStage;
	/**
	 * The current step the robot is in.
	 */
	private EStep mCurrentStep;
	/**
	 * A list of objects that want to receive events each time the robot
	 * encounters an uncatched exception in {@link #pulse()}.
	 */
	private final List<IRobotEncounteredExceptionListener> mExceptionListeners;
	/**
	 * Whether the robot has stopped, i.e. finished the algorithm.
	 */
	private boolean mHasStopped;
	/**
	 * The unique id of this robot.
	 */
	private final int mId;
	/**
	 * The manager used for constructing knowledge of nodes.
	 */
	private final KnowledgeManager mKnowledgeManager;
	/**
	 * The object that provides the local storage of nodes.
	 */
	private final ILocalStorage mLocalStorage;
	/**
	 * The local storage data of the node currently located at retrieved in the
	 * last READ stage.
	 */
	private NestedMap2<Integer, Integer, Information> mLocalStorageData;
	/**
	 * Whether the movement in the last move stage was done from parent to a
	 * child or from child to its parent.
	 */
	private boolean mMovedFromParentToChildLastMoveStage;
	/**
	 * The port used in the last move stage.
	 */
	private int mPortUsedLastMoveStage;
	/**
	 * A list of objects that want to receive events each time this robot moves
	 * to another node.
	 */
	private final List<IRobotMovedListener> mRobotMovedListeners;
	/**
	 * The number of the round that is to be executed next.
	 */
	private int mRoundCounter;
	/**
	 * The number of the step that is to be executed next.
	 */
	private int mStepCounter;

	/**
	 * Creates a new robot with a unique id starting at the given node.
	 * 
	 * @param id
	 *            The unique id of the robot
	 * @param startingNode
	 *            The node the robot starts in
	 * @param localStorage
	 *            The object that provides the local storage of nodes
	 * @param robotMovedListeners
	 *            A list of objects that want to receive events each time this
	 *            robot moves to another node
	 * @param exceptionListeners
	 *            A list of objects that want to receive events each time the
	 *            robot encounters an uncatched exception in {@link #pulse()}.
	 */
	public Robot(final int id, final ITreeNode startingNode, final ILocalStorage localStorage,
			final List<IRobotMovedListener> robotMovedListeners,
			final List<IRobotEncounteredExceptionListener> exceptionListeners) {
		this.mId = id;
		this.mCurrentNode = startingNode;
		this.mLocalStorage = localStorage;
		this.mRobotMovedListeners = robotMovedListeners;
		this.mExceptionListeners = exceptionListeners;
		this.mLocalStorageData = null;
		this.mKnowledgeManager = new KnowledgeManager();

		this.mCurrentStep = EStep.INITIAL;
		this.mCurrentStage = EStage.MOVE;
		this.mHasStopped = false;
		this.mStepCounter = 1;
		this.mRoundCounter = 0;
		this.mPortUsedLastMoveStage = Information.STAY_PORT;
		this.mMovedFromParentToChildLastMoveStage = true;
	}

	/**
	 * Compares robots by their unique id, ascending.
	 */
	@Override
	public int compareTo(final Robot other) {
		return Integer.compare(this.mId, other.getId());
	}

	/**
	 * Gets the unique id of this robot.
	 * 
	 * @return The unique id of this robot
	 */
	public int getId() {
		return this.mId;
	}

	/**
	 * Gets the tree node the robot is currently located at.
	 * 
	 * @return The tree node the robot is currently located at
	 */
	public ITreeNode getLocation() {
		return this.mCurrentNode;
	}

	/**
	 * Whether the robot has stopped, i.e. finished the algorithm.
	 * 
	 * @return <tt>True</tt> if the robot has stopped, <tt>false</tt> otherwise
	 */
	public boolean hasStopped() {
		return this.mHasStopped;
	}

	/**
	 * Pulses the robot which demands him to execute one step of the algorithm.
	 * 
	 * @return <tt>True</tt> if the robot has stopped because he finished the
	 *         algorithm, <tt>false</tt> otherwise
	 */
	public boolean pulse() {
		try {
			// Directly return if already stopped
			if (hasStopped()) {
				return true;
			}

			// Execute the stage of the current step
			executeStage(this.mCurrentStep, this.mCurrentStage);

			// Select the next step and stage and finish the pulse
			selectNextStepAndStage();
			return hasStopped();
		} catch (final Throwable e) {
			// Forward every exception to listeners
			for (final IRobotEncounteredExceptionListener listener : this.mExceptionListeners) {
				listener.encounteredException(this, e);
			}
			// Then fail
			throw e;
		}
	}

	/**
	 * Executes the stage of the given step.
	 * 
	 * @param step
	 *            The step to execute for
	 * @param stage
	 *            The stage to execute
	 */
	private void executeStage(final EStep step, final EStage stage) {
		// NOP step
		if (step == EStep.NOP) {
			// Do nothing in this step
			return;
		}

		// The actions for stages WRITE and READ are equal in all steps
		if (stage == EStage.WRITE) {
			writeAction();
			return;
		}
		if (stage == EStage.READ) {
			readAction();
			return;
		}

		// Move stage
		if (stage == EStage.MOVE) {
			// Initial step
			if (step == EStep.INITIAL) {
				// Only set the star port which indicates the initial movement
				// to the root
				this.mPortUsedLastMoveStage = Information.STAR_PORT;
				this.mMovedFromParentToChildLastMoveStage = true;
				return;
			}

			// Regular step
			if (step == EStep.REGULAR) {
				// Construct the knowledge for this round which is used to
				// determine the action to be taken
				final Knowledge knowledge = this.mKnowledgeManager.constructKnowledge(this.mRoundCounter,
						this.mCurrentNode, this.mLocalStorageData);

				// Determine the action to perform based on the given knowledge
				final int port = KnowledgeManager.robotAction(this.mId, knowledge, this.mCurrentNode);

				if (port == Information.STAR_PORT && this.mCurrentNode.isRoot()) {
					// The robot stops as it has finished the algorithm
					stayAtNode();
					setStopped();
					return;
				}

				if (port == Information.STAY_PORT) {
					// Stay at the current node and do not move
					stayAtNode();
					return;
				}

				if (port == Information.PARENT_PORT) {
					// Move to the parent of the current node
					moveAlongEdge(this.mCurrentNode, knowledge.getParentPort(), this.mCurrentNode.getParent().get(),
							false);
					return;
				}

				// Move along the given port to a child of the current node
				moveAlongEdge(this.mCurrentNode, port, this.mCurrentNode.getChild(port), true);
				return;
			}

			// Update step
			if (step == EStep.UPDATE) {
				// As we have moved in the last step the knowledge for the
				// current node is unknown or not up to date. However we need
				// the knowledge to determine if the subtree is finished so we
				// construct the knowledge.
				final Knowledge knowledge = this.mKnowledgeManager.constructKnowledge(this.mRoundCounter,
						this.mCurrentNode, this.mLocalStorageData);

				// The node is finished if all children have finished
				if (!knowledge.getUnfinishedChildrenPorts().isEmpty()) {
					// The node is not finished so we stay
					stayAtNode();
					return;
				}

				// Do not move up if child is root.
				if (this.mCurrentNode.isRoot()) {
					stayAtNode();
					return;
				}

				// Temporarily move to the parent to inform it that its child
				// has finished.
				moveAlongEdge(this.mCurrentNode, knowledge.getParentPort(), this.mCurrentNode.getParent().get(), false);
				return;
			}

			// Return step
			if (step == EStep.RETURN) {
				// Undo the temporary move of the UPDATE stage. If we executed
				// such a move then we wrote a message in the last stage at the
				// current node.
				final int stepCounterOfLastUpdateStage = this.mStepCounter - 1;
				final Information info = this.mLocalStorageData.get(Integer.valueOf(stepCounterOfLastUpdateStage),
						Integer.valueOf(this.mId));

				if (info == null) {
					// We did not execute such a temporary move so we do not
					// undo anything
					stayAtNode();
					return;
				}

				// Undo the temporary move of the last stage
				final int portOfChild = info.getPort();
				moveAlongEdge(this.mCurrentNode, portOfChild, this.mCurrentNode.getChild(portOfChild), true);
				return;
			}
		}

		throw new AssertionError();
	}

	/**
	 * Moves the robot along the given edge.
	 * 
	 * @param source
	 *            The source node of the edge
	 * @param port
	 *            The port that identifies the edge
	 * @param destination
	 *            The destination of the edge
	 * @param fromParent
	 *            <tt>True</tt> if the movement is from parent to its child or
	 *            <tt>false</tt> if it is from a child to its parent
	 */
	private void moveAlongEdge(final ITreeNode source, final int port, final ITreeNode destination,
			final boolean fromParent) {
		this.mPortUsedLastMoveStage = port;
		this.mMovedFromParentToChildLastMoveStage = fromParent;
		this.mCurrentNode = destination;

		// Notify listeners
		for (final IRobotMovedListener listener : this.mRobotMovedListeners) {
			listener.movedTo(this, source, destination);
		}
	}

	/**
	 * Reads all information from the current node and updates the personal
	 * knownledge.
	 */
	private void readAction() {
		// Update the current local storage data
		this.mLocalStorageData = this.mLocalStorage.read(this.mCurrentNode);
	}

	/**
	 * Selects the step and stage to be used for the next pulse depending on the
	 * current step and stage.
	 */
	private void selectNextStepAndStage() {
		boolean doUpdateStep = false;

		// Update the stage
		if (this.mCurrentStage == EStage.MOVE) {
			this.mCurrentStage = EStage.WRITE;
		} else if (this.mCurrentStage == EStage.WRITE) {
			this.mCurrentStage = EStage.READ;
		} else if (this.mCurrentStage == EStage.READ) {
			doUpdateStep = true;
			this.mCurrentStage = EStage.MOVE;
		} else {
			throw new AssertionError();
		}

		// Update the step
		if (!doUpdateStep) {
			return;
		}
		this.mStepCounter++;
		if (this.mCurrentStep == EStep.INITIAL) {
			this.mCurrentStep = EStep.NOP;
		} else if (this.mCurrentStep == EStep.NOP) {
			this.mRoundCounter++;
			this.mCurrentStep = EStep.REGULAR;
		} else if (this.mCurrentStep == EStep.REGULAR) {
			this.mCurrentStep = EStep.UPDATE;
		} else if (this.mCurrentStep == EStep.UPDATE) {
			this.mCurrentStep = EStep.RETURN;
		} else if (this.mCurrentStep == EStep.RETURN) {
			this.mRoundCounter++;
			this.mCurrentStep = EStep.REGULAR;
		} else {
			throw new AssertionError();
		}
	}

	/**
	 * Sets the robot to stopped. This means it has finished the algorithm and
	 * will refuse further {@link #pulse()} calls.
	 */
	private void setStopped() {
		this.mHasStopped = true;
	}

	/**
	 * Move action that stays at the current node.
	 */
	private void stayAtNode() {
		this.mPortUsedLastMoveStage = Information.STAY_PORT;
		this.mMovedFromParentToChildLastMoveStage = true;
	}

	/**
	 * Writes the current information to the current node.
	 */
	private void writeAction() {
		// Write information only if robot moved in the last stage
		if (this.mPortUsedLastMoveStage == Information.STAY_PORT) {
			return;
		}

		// Write (step, id, port) to current node. This means that the robot
		// specified by the given id moved in the given step to the current node
		// by using the given port.
		final Information info = new Information(this.mStepCounter, this.mId, this.mPortUsedLastMoveStage,
				this.mMovedFromParentToChildLastMoveStage);
		this.mLocalStorage.write(info, this.mCurrentNode);
	}
}
