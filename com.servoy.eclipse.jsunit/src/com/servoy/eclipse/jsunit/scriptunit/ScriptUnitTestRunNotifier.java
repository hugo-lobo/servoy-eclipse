/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2013 Servoy BV

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU Affero General Public License as published by the Free
 Software Foundation; either version 3 of the License, or (at your option) any
 later version.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License along
 with this program; if not, see http://www.gnu.org/licenses or write to the Free
 Software Foundation,Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301
 */

package com.servoy.eclipse.jsunit.scriptunit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import junit.framework.AssertionFailedError;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestListener;
import junit.framework.TestResult;
import junit.framework.TestSuite;

import org.eclipse.dltk.testing.ITestingClient;
import org.eclipse.dltk.testing.model.ITestRunSession;

import com.servoy.eclipse.jsunit.runner.JSUnitTestListenerHandler;

/**
 * Notifies the current running dltk TestRunSession about the test states .
 *  It is attached to a junit TestResult object and listens for the TestListener callbacks.
 * @author obuligan
 */
public class ScriptUnitTestRunNotifier implements TestListener
{
	private boolean testTreeSent = false;
	private final List<Test> testList;
	private final ITestingClient testingClient;
	private final TestResult testResult;
	private final int nrOfTests;
	private int currentTestCounter = 0;
	long startTime = 0;

	public ScriptUnitTestRunNotifier(List<Test> testList, TestResult testResult)
	{
		// create a copy of the test list and reverse the testCases (but not the TestSuites)
		ArrayList<Test> copy = new ArrayList<Test>();
		copy.addAll(testList);
		TestSuite currentSuite = null;
		for (Test test : copy)
		{
			if (test instanceof TestSuite)
			{
				currentSuite = (TestSuite)test;
			}
			else
			{//tests are passed in reverse order from the list so calculate the Id from testCount() down
				List<Test> sublist = copy.subList(copy.indexOf(currentSuite) + 1, copy.indexOf(currentSuite) + currentSuite.testCount() + 1);
				Collections.reverse(sublist);

//				int testId = 2 * testList.indexOf(currentSuite) + currentSuite.testCount() - testList.indexOf(test) + 1;
//				testingClient.testTree(testId, ((TestCase)test).getName(), false, 1);
			}
		}


		this.testList = copy;
		this.nrOfTests = testList.get(0).countTestCases();
		this.testingClient = getScriptTestRunnerClient();
		this.testResult = testResult;

	}

	private void sendStartRun()
	{
		isStopRequested();
		testingClient.testRunStart(nrOfTests);
		startTime = System.currentTimeMillis();

	}

	private void sendTestTree()
	{
		isStopRequested();
		for (Test test : testList)
		{
			if (test instanceof TestSuite)
			{
				testingClient.testTree(testList.indexOf(test), ((TestSuite)test).getName(), true, ((TestSuite)test).testCount());
			}
			else
			{
				testingClient.testTree(testList.indexOf(test), ((TestCase)test).getName(), false, 1);
			}
		}

	}

	private void sendTestStarted(Test test)
	{
		isStopRequested();
		testingClient.testStarted(testList.indexOf(test), getTestName(test));

	}

	/**
	 * also automatically sends end of test run if it is the last test
	 * @param test
	 */
	private void sendTestEnded(Test test)
	{
		isStopRequested();
		testingClient.testEnded(testList.indexOf(test), getTestName(test));
		if ((currentTestCounter == nrOfTests) || (isStopRequested())) testingClient.testTerminated((int)(System.currentTimeMillis() - startTime));

	}

	/**
	 * Used both to send failure and send error depending on argument type Error or  AssertionFailedError 
	 * @param test
	 * @param t
	 */
	private void sendTestFailure(Test test, Throwable t)
	{
		isStopRequested();
		if (t instanceof AssertionFailedError)
		{
			AssertionFailedError fail = (AssertionFailedError)t;
			testingClient.testFailed(ITestingClient.FAILED, testList.indexOf(test), ((TestCase)test).getName());
			String asertionFailedPattern = "AssertionFailedError: Expected:<(.+)>, but was:<(.+)>";
			testingClient.testExpected(fail.getMessage().replaceAll(asertionFailedPattern, "$1"));
			testingClient.testActual(fail.getMessage().replaceAll(asertionFailedPattern, "$2"));
		}
		else
		{
			testingClient.testError(testList.indexOf(test), getTestName(test));
		}
		//send traces
		testingClient.traceStart();
		testingClient.traceMessage(t.getMessage());
		for (StackTraceElement frame : t.getStackTrace())
		{
			testingClient.traceMessage(JSUnitTestListenerHandler.getFileNameFromJavaType(frame.toString()));
		}
		testingClient.traceEnd();

	}

	private boolean isStopRequested()
	{
		if (testingClient instanceof RemoteScriptUnitRunnerClient)
		{
			if (((RemoteScriptUnitRunnerClient)testingClient).isStopRequested())
			{
				testResult.stop();
				return true;
			}
		}
		return false;
	}

	private String getTestName(Test test)
	{
		String name = null;
		if (test instanceof TestCase)
		{
			name = ((TestCase)test).getName();
		}
		else if (test instanceof TestSuite)
		{
			name = ((TestSuite)test).getName();
		}
		return name;
	}


	private ITestingClient getScriptTestRunnerClient()
	{
		ITestRunSession testRunSession = org.eclipse.dltk.testing.DLTKTestingPlugin.getModel().getTestRunSession(
			com.servoy.eclipse.jsunit.launch.JSUnitLaunchConfigurationDelegate.getCurrentLaunch());
		if (testRunSession != null)
		{
			return testRunSession.getTestRunnerClient();
		}
		else
		{
			return null;
		}
	}

	@Override
	public void addError(Test test, Throwable t)
	{
		this.sendTestFailure(test, t);

	}

	@Override
	public void addFailure(Test test, AssertionFailedError t)
	{
		this.sendTestFailure(test, t);

	}

	@Override
	public void endTest(Test test)
	{
		this.sendTestEnded(test);
	}

	@Override
	public void startTest(Test test)
	{
		if (!testTreeSent)
		{
			sendStartRun();
			sendTestTree();
			testTreeSent = true;
		}
		currentTestCounter++;
		this.sendTestStarted(test);
	}


}
