package com.rufana.instantlore.server.task;

public class TaskFailure extends RuntimeException {
	public TaskFailure(String message) {
		super(message);
	}
}
