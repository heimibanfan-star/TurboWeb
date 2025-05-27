package org.example.lifecycle;

import top.turboweb.core.listener.TurboWebListener;

public class TwoListener implements TurboWebListener {
	@Override
	public void beforeServerInit() {
		System.out.println("服务器初始化之前-two");
	}
	@Override
	public void afterServerStart() {
		System.out.println("服务器启动之后-two");
	}
}
