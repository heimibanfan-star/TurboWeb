package org.example.lifecycle;

import top.turboweb.core.listener.TurboWebListener;

public class OneListener implements TurboWebListener {
	@Override
	public void beforeServerInit() {
		System.out.println("服务器初始化之前-one");
	}
	@Override
	public void afterServerStart() {
		System.out.println("服务器启动之后-one");
	}
}
