package com.github.nedp.comp90015.proj2.job.worker.master;

import com.github.nedp.comp90015.proj2.job.Job;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Manages a set of {@link Worker}s, and allocates {@link Job}s to them.
 * <p>
 * Synchronisation is provided to prevent concurrent access
 * to the interior worker collection.
 * <p>
 * In the current implementation, round robin allocation is used.
 *
 * @author nedp
 */
public class WorkerPool {
  @NotNull
  protected final Set<Worker> workerSet; // Must be a Set to avoid duplicates.

  @NotNull
  private final List<Worker> workerList; // Must be a List for round robin.
  private int iWorker = 0; // Current worker for round robin allocation.


  /**
   * Populates a new WorkerPool with the supplied {@link Worker}s.
   * <p>
   * The supplied list will only be used for initialisation,
   * and may then be safely modified.
   *
   * @param initialWorkers a list of workers which should initially
   * be contained in the pool.
   */
  public WorkerPool(@NotNull Collection<Worker> initialWorkers) {
    this.workerList = new ArrayList<>(initialWorkers);
    this.workerSet = new HashSet<>(initialWorkers);
  }

  /**
   * Creates a WorkerPool with no initial {@link Worker}s.
   */
  public WorkerPool() {
    this.workerList = new ArrayList<>();
    this.workerSet = new HashSet<>();
  }

  /**
   * Allocates the specified {@link Job} to a {@link Worker} and
   * delegates execution of the Job to the Worker.
   * <p>
   * If there are no available workers, an exception is thrown.
   *
   * @param job the job to be executed, not null.
   * @return the {@link Result} of the job.
   * @throws WorkerUnavailableException if there are no
   *                                    Workers to which the Job may be allocated to.
   */
  @NotNull
  Result allocateAndExecute(@NotNull Job job) throws WorkerUnavailableException {
    // Choose a worker for the job.
    final Optional<Worker> chosenWorker = this.workerFor(job);

    // Have the chosen worker execute the job.
    final Worker worker = chosenWorker.orElseThrow(WorkerUnavailableException::new);

    return worker.execute(job);
  }


  /**
   * Chooses a suitable {@link Worker} for {@link Job} allocation.
   * <p>
   * This implementation uses round robin allocation.
   * Overriders may choose to make use of {@code job},
   * but this implementation does not.
   *
   * @param job the Job to be allocated.
   * @return Optional.of(the chosen Worker),
   * or Optional.empty() if there is no such worker.
   */
  @NotNull
  protected synchronized Optional<Worker> workerFor(@NotNull Job job) {
    final int size = this.workerSet.size();
    if (size == 0) {
      return Optional.empty();
    }
    int i = this.iWorker == size ? 0 : this.iWorker;
    Worker chosenWorker = this.workerList.get(i);
    while (chosenWorker.status() != Worker.Status.RUNNING) {
      i += 1;
      assert (i <= size);
      if (i == size) {
        i = 0;
      }
      // If we tried all workers, there's no worker for the Job.
      if (i == this.iWorker) {
        return Optional.empty();
      }
      chosenWorker = this.workerList.get(i);
    }
    this.iWorker = i + 1;
    return Optional.of(chosenWorker);
  }

  /**
   * Adds a new {@link Worker} to the pool, if it isn't already included.
   *
   * @param worker the Worker to be added, not null.
   * @return true if the Worker was added to the pool,
   * and false if the Worker was already present.
   */
  public synchronized boolean add(@NotNull Worker worker) {
    // Don't allow duplicate workers.
    if (this.workerSet.contains(worker)) {
      return false;
    }

    boolean ok = this.workerSet.add(worker);
    assert (ok);
    ok = this.workerList.add(worker);
    assert (ok);
    return true;
  }

  /**
   * Retrieves a list of all {@link Worker}s in the pool.
   * <p>
   * Includes known Workers regardless of their status.
   * Changing the returned list will not affect this WorkerPool.
   *
   * @return a new {@link List<Worker>} containing all the workers
   * known by this WorkerPool, in the order they were added.
   */
  @NotNull
  public synchronized List<Worker> workerList() {
    return new ArrayList<>(this.workerList);
  }
}
