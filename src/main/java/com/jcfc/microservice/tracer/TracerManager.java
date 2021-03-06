package com.jcfc.microservice.tracer;


import brave.Tracing;
import brave.context.log4j2.ThreadContextCurrentTraceContext;
import brave.propagation.B3Propagation;
import brave.propagation.CurrentTraceContext;
import brave.propagation.ExtraFieldPropagation;
import brave.sampler.BoundarySampler;
import com.jcfc.microservice.tracer.utils.NetworkUtils;
import zipkin2.Endpoint;
import zipkin2.Span;
import zipkin2.codec.SpanBytesEncoder;
import zipkin2.reporter.AsyncReporter;
import zipkin2.reporter.Reporter;
import zipkin2.reporter.Sender;
import zipkin2.reporter.amqp.RabbitMQSender;

import java.util.concurrent.TimeUnit;


/**
 * 日志跟踪管理器
 * 1.加载一些环境变量
 * 2.存放tracing上下文
 *
 * @author zhangjinpeng
 * @version 1.0
 */
public class TracerManager {

	public static final String ANNO_CS = "cs";
	public static final String ANNO_CR = "cr";
	public static final String ANNO_SR = "sr";
	public static final String ANNO_SS = "ss";

	static private final String ZIPKIN_SENDER_RABBITMQ_ADDRESSES = "zipkin.sender.rabbitmq.addresses";
	static private final String ZIPKIN_SENDER_RABBITMQ_USERNAME = "zipkin.sender.rabbitmq.username";
	static private final String ZIPKIN_SENDER_RABBITMQ_PASSWORD = "zipkin.sender.rabbitmq.password";
	static private final String TRACER_SERVER_NAME = "tracer.server.name";
	static private final String TRACER_CONTEXT_NAME = "tracer.context.name";
	static private final String TRACER_SAMPLER_PERCENTAGE = "tracer.sampler.percentage";

	static private Tracing tracing;

	static private final CurrentTraceContext CURRENT_TRACE_CONTEXT = findCurrentTraceContext();

	static {
		/*
		 * 初始化Tracing
		 */
		Sender sender = RabbitMQSender.newBuilder()
				.addresses(TracerProperties.getProperty(ZIPKIN_SENDER_RABBITMQ_ADDRESSES))
				.username(TracerProperties.getProperty(ZIPKIN_SENDER_RABBITMQ_USERNAME))
				.password(TracerProperties.getProperty(ZIPKIN_SENDER_RABBITMQ_PASSWORD))
				.build();
		Reporter<Span> asyncReporter = AsyncReporter.builder(sender)
				.closeTimeout(500, TimeUnit.MILLISECONDS)
				.messageTimeout(500, TimeUnit.MILLISECONDS)
				.build(SpanBytesEncoder.JSON_V2);
		String percentage = TracerProperties.getProperty(TRACER_SAMPLER_PERCENTAGE, "1.0");
		tracing = Tracing.newBuilder()
				.sampler(BoundarySampler.create(Float.valueOf(percentage)))
				.localEndpoint(Endpoint.newBuilder().serviceName(TracerProperties.getProperty(TRACER_SERVER_NAME, "tracer-server")).ip(NetworkUtils.getLocalHost()).build())
				.spanReporter(asyncReporter)
				.supportsJoin(true)//是否合并客户端和服务端的span
				.propagationFactory(ExtraFieldPropagation.newFactory(B3Propagation.FACTORY, "localhost"))
				.currentTraceContext(CURRENT_TRACE_CONTEXT)
				.build();
	}

	private static CurrentTraceContext findCurrentTraceContext() {
		String contextName = TracerProperties.getProperty(TRACER_CONTEXT_NAME);
		CurrentTraceContext currentTraceContext = null;
		if(contextName != null){
			switch (contextName){
				case "log4j":
					try {
						Class.forName("brave.context.log4j12.MDCCurrentTraceContext");
						currentTraceContext = brave.context.log4j12.MDCCurrentTraceContext.create();
						break;
					} catch (ClassNotFoundException e) {
						;
					}
				case "slf4j":
					try {
						Class.forName("brave.context.slf4j.MDCCurrentTraceContext");
						currentTraceContext = brave.context.slf4j.MDCCurrentTraceContext.create();
						break;
					} catch (ClassNotFoundException e) {
						;
					}
				case "log4j2":
					try {
						Class.forName("brave.context.log4j2.ThreadContextCurrentTraceContext");
						currentTraceContext = ThreadContextCurrentTraceContext.create();
						break;
					} catch (ClassNotFoundException e) {
						;
					}
				default:
					//走InheritableThreadLocal，在日志中就绑定不上了
					currentTraceContext = CurrentTraceContext.Default.create();
					break;
			}
			return currentTraceContext;
		}
		else {
			return CurrentTraceContext.Default.create();
		}
	}

	public static Tracing getTracing(){
		return tracing;
	}



}
