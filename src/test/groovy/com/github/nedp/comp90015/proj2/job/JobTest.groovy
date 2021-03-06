package com.github.nedp.comp90015.proj2.job

import spock.lang.Ignore
import spock.lang.Specification
import test_jobs.DoNothing

import java.nio.file.Files

import static com.github.nedp.comp90015.proj2.job.Status.*

/**
 * Created by nedp on 11/05/15.
 */
class JobTest extends Specification {
  static def FILES = new Job.Files("src/test/resources/test_jobs/do_nothing");
  static def JAR = FILES.jar;
  static def IN = FILES.in;
  static def OUT = FILES.out;
  static def LOG = FILES.log;
  static def NONE = new File("")

  static def BAD_FILES = new Job.Files("src/test/resources/test_jobs/fail_nothing");

  static def WORD_COUNT_JAR = new File("src/test/resources/SampleJob/wordcount.jar")
  static def WORD_COUNT_IN = new File("src/test/resources/SampleJob/sample-input.txt")
  static def WORD_COUNT_OUT = new File("src/test/resources/SampleJob/wordcount.out")
  static def WORD_COUNT_LOG = new File("src/test/resources/SampleJob/wordcount.log")
  static def WORD_COUNT_FILES = new Job.Files(WORD_COUNT_JAR, WORD_COUNT_IN,
      WORD_COUNT_OUT, WORD_COUNT_LOG)
  static def WORD_COUNT_WANT = new File("src/test/resources/SampleJob/sample-output.txt")

  static def USE_MEMORY_FILES = new Job.Files("src/test/resources/test_jobs/use_memory");
  static def TAKE_TIME_FILES = new Job.Files("src/test/resources/test_jobs/take_time");

  Job underTest;
  StatusTracker tracker;

  def setup() {
    deleteOutput()
    tracker = Mock(StatusTracker);
  }

  def cleanup() {
    deleteOutput()
  }

  def "delegates #currentStatus to tracker#current"() {
    given:
    1 * tracker.current() >> want
    0 * _
    aDefaultJob()

    expect:
    thatTheJobHasStatus(want)

    where:
    want << [WAITING, RUNNING, FINISHED, FAILED]
  }

  def "runs using the output files, and finishes"() {
    given:
    aDefaultJob()

    when:
    theJobIsRun()

    then:
    OUT.exists()
    LOG.exists()
    1 * tracker.start()
    1 * tracker.finish(true)
    0 * _
  }

  def "detects failure to invoke"() {
    given:
    aJobWithFiles(new Job.Files(jar, in_, out, log))

    when:
    theJobIsRun()
    then:
    1 * tracker.start()
    1 * tracker.finish(false)
    0 * _

    where:
    jar  | in_  | out  | log
    NONE | IN   | OUT  | LOG
    JAR  | NONE | OUT  | LOG
    JAR  | IN   | NONE | LOG
    JAR  | IN   | OUT  | NONE
  }

  def "detects non-zero exit code failure"() {
    given:
    aBadJob()

    when:
    theJobIsRun()

    then:
    1 * tracker.start()
    1 * tracker.finish(false)
    0 * _
  }

  def "routes both stderr and stdout to _.log"() {
    given:
    aDefaultJob()

    when:
    theJobIsRun()
    def lines = Files.readAllLines(LOG.toPath())

    then:
    1 * tracker.start()
    1 * tracker.finish(true)
    0 * _
    and:
    lines[0] == DoNothing.STDOUT
    lines[1] == DoNothing.STDERR
    lines.size() == 2
  }

  def "Files is correct"() {
    given:
    def jar = new File("1");
    def in_ = new File("2");
    def out = new File("3");
    def log = new File("4");
    def files = new Job.Files(jar, in_, out, log)

    expect:
    jar == files.jar
    in_ == files.in
    out == files.out
    log == files.log
  }

  @Ignore("Takes a long time")
  def "Runs wordcount with expected output"() {
    given:
    aWordCountJob()

    when:
    theJobIsRun()
    then:
    1 * tracker.start()
    1 * tracker.finish(true)
    0 * _
    and:
    def want = Files.readAllLines(WORD_COUNT_WANT.toPath())
    def got = Files.readAllLines(WORD_COUNT_OUT.toPath())
    want.equals(got)
  }

  @Ignore("Takes a long time")
  def "Jobs timeout correctly"() {
    given:
    underTest = new Job(TAKE_TIME_FILES, tracker, Job.NO_LIMIT, timeout)

    when:
    theJobIsRun()
    then:
    1 * tracker.start()
    1 * tracker.finish(ok)
    0 * _

    where:
    timeout || ok
    -1      || true
    0       || true
    1       || false
    4       || false
    // Grace area - actual expected time taken is 5 seconds.
    6 || true
  }

  def "Jobs run out of memory correctly"() {
    given:
    underTest = new Job(USE_MEMORY_FILES, tracker, memLimit, Job.NO_TIMEOUT)

    when:
    theJobIsRun()
    then:
    1 * tracker.start()
    1 * tracker.finish(ok)
    0 * _

    where:
    memLimit || ok
    -1       || true
    0        || true
    1        || false
    2        || false
    3        || false
    // Grace area - actual expected memory required is just over 4MB.
    6 || true
    7 || true
  }

  def "#name is correct"() {
    given:
    aJobWithFiles(files)
    expect:
    thatTheJobHasName(files.jar.name)
    where:
    files << [FILES, BAD_FILES, WORD_COUNT_FILES, USE_MEMORY_FILES, TAKE_TIME_FILES]
  }

  def "toJSON and fromJSON work correctly together"() {
    // Can't catch file errors
    given:
    String baseName = name + "/" + name
    underTest = new Job(new Job.Files(baseName), tracker, memoryLimit, timeout);
    Job result = Job.fromJSON(underTest.toJSON())

    expect: "the initial Job and processed Job to be the same"
    (String) result.files.jar == baseName + ".jar"
    (String) result.files.in == baseName + ".in"
    (String) result.files.out == baseName + ".out"
    (String) result.files.log == baseName + ".log"
    result.name() == underTest.name()
    result.memoryLimit == underTest.memoryLimit
    result.timeout == underTest.timeout

    cleanup: "remove files from the Job"
    try {
      new File(baseName + ".jar").delete()
    } catch (_) {}
    try {
      new File(baseName + ".in").delete()
    } catch (_) {}
    try {
      new File(baseName + ".out").delete()
    } catch (_) {}
    try {
      new File(baseName + ".log").delete()
    } catch (_) {}
    try {
      new File(name).delete()
    } catch (_) {}

    where:
    name  | memoryLimit | timeout
    "abc" | 0           | 5
    "xyz" | 5           | 0
    "ijk" | -1          | -1
  }

  def aDefaultJob() {
    aJobWithFiles(FILES)
  }

  def aBadJob() {
    aJobWithFiles(BAD_FILES)
  }

  def aWordCountJob() {
    aJobWithFiles(WORD_COUNT_FILES)
  }

  def aJobWithFiles(Job.Files files) {
    underTest = new Job(files, tracker)
  }

  def theJobIsRun() {
    underTest.run()
  }

  def thatTheJobHasName(String name) {
    underTest.name() == name
  }

  def thatTheJobHasStatus(Status status) {
    underTest.currentStatus() == status
  }

  def deleteOutput() {
    try {
      FILES.out.delete()
    } catch (_) {
    }
    try {
      FILES.log.delete()
    } catch (_) {
    }
    try {
      BAD_FILES.out.delete()
    } catch (_) {
    }
    try {
      BAD_FILES.log.delete()
    } catch (_) {
    }
    try {
      WORD_COUNT_OUT.delete()
    } catch (_) {
    }
    try {
      WORD_COUNT_LOG.delete()
    } catch (_) {
    }
    try {
      USE_MEMORY_FILES.out.delete()
    } catch (_) {
    }
    try {
      USE_MEMORY_FILES.log.delete()
    } catch (_) {
    }
    try {
      TAKE_TIME_FILES.out.delete()
    } catch (_) {
    }
    try {
      TAKE_TIME_FILES.log.delete()
    } catch (_) {
    }
  }
}
