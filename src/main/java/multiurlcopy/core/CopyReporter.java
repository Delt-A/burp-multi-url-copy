package multiurlcopy.core;

/** Where a copy attempt's result is announced: the settings tab and Burp's log. */
public interface CopyReporter {

    void report(CopyOutcome outcome);
}
