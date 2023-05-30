package wotproxy;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;

import city.sane.wot.Wot;
import city.sane.wot.WotException;
import city.sane.wot.thing.ConsumedThing;
import city.sane.wot.thing.Context;
import city.sane.wot.thing.ExposedThing;
import city.sane.wot.thing.Thing;
import city.sane.wot.thing.action.ThingAction;
import city.sane.wot.thing.event.ThingEvent;
import city.sane.wot.thing.property.ThingProperty;
import city.sane.wot.thing.security.NoSecurityScheme;

public class WotProxyFactory {
	private static final Logger LOG = Logger.getLogger(WotProxyFactory.class.getName());

	/**
	 * Creates a proxy for the given Thing Description.
	 * The proxy supports all the protocol supported by the provided servient.
	 * The created ExposedThing is not exposed!
	 * 
	 * @param td the thing description
	 * @param id the id of the proxy
	 * @param wot the servient that hosts the proxy        exposedProxy.setSecurity(List.of("nosec_sc"));
        exposedProxy.setSecurityDefinitions(Map.of("nosec_sc", new NoSecurityScheme()));

	 * @return
	 * @throws InterruptedException
	 * @throws ExecutionException
	 * @throws WotException
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static ExposedThing createProxyAndExpose(String td, String id, Wot wot) throws InterruptedException, ExecutionException, WotException {
		//consume the thing
        Thing thing = Thing.fromJson(td);
		ConsumedThing consumedThing = wot.consume(thing);

		//create a proxy
		Thing proxyThing = new Thing.Builder()
	        .setId(id)
	        .setTitle(consumedThing.getTitle() + " proxy")
	        .setDescription("Proxy for: " + consumedThing.getDescription())
	        .setSecurity(List.of("nosec_sc"))
	        .setSecurityDefinitions(Map.of("nosec_sc", new NoSecurityScheme()))
            .setObjectContext(new Context("https://www.w3.org/2019/wot/td/v1"))
	        .build();

		ExposedThing exposedProxy = wot.produce(proxyThing);

		//replicate the properties
		for(Object propNameObject : thing.getProperties().keySet()) {
			if(propNameObject instanceof String propName) {
				ThingProperty thingProperty = thing.getProperty(propName);
				ThingProperty proxyProperty = new ThingProperty.Builder().
					setDescription(thingProperty.getDescriptions()).
					//setForms(thingProperty.getForms()).
					setObjectType(thingProperty.getObjectType()).
					setObservable(thingProperty.isObservable()).
					setOptionalProperties(thingProperty.getOptionalProperties()).
					setReadOnly(thingProperty.isReadOnly()).
					setType(thingProperty.getType()).
					setUriVariables(thingProperty.getUriVariables()).
					setWriteOnly(thingProperty.isWriteOnly()).
					build();
				Supplier<CompletableFuture<Object>> readHandler = () -> {
					LOG.info("Proxy "+id+" proxying reading of property: "+propName);
					return consumedThing.getProperty(propName).read();
				};
				Function<Object, CompletableFuture<Object>> writeHandler = (value) -> {
					LOG.info("Proxy "+id+" proxying writing of property: "+propName);
					return consumedThing.getProperty(propName).write(value);
				};
				exposedProxy.addProperty(propName, proxyProperty, readHandler, writeHandler);
			}
		}
		//replicate the actions
		for(Object actionNameObject : thing.getActions().keySet()) {
			if(actionNameObject instanceof String actionName) {
				ThingAction thingAction = thing.getAction(actionName);
				ThingAction proxyAction = new ThingAction.Builder().
					setDescription(thingAction.getDescriptions()).
					setForms(thingAction.getForms()).
					setInput(thingAction.getInput()).
					setObjectType(thingAction.getObjectType()).
					setOutput(thingAction.getOutput()).
					setUriVariables(thingAction.getUriVariables()).
					build();
				//TODO: handle one way actions with special cases?
				exposedProxy.addAction(actionName, proxyAction, 
					(input, options) -> {
						LOG.info("Proxy "+id+" proxying action "+actionName+" - options: "+options+" - input: "+input);
						return consumedThing.getAction(actionName).invoke(input == null ? Collections.emptyMap():(Map)input);
					});
			}
		}
		//replicate the events
		for(Object eventNameObject : thing.getEvents().keySet()) {
			if(eventNameObject instanceof String eventName) {
				ThingEvent thingEvent = thing.getEvent(eventName);
				ThingEvent proxyEvent = new ThingEvent.Builder().
					setData(thingEvent.getData()).
					setDescription(thingEvent.getDescriptions()).
					setForms(thingEvent.getForms()).
					setObjectType(thingEvent.getObjectType()).
					setType(thingEvent.getType()).
					setUriVariables(thingEvent.getUriVariables()).
					build();
				exposedProxy.addEvent(eventName, proxyEvent);
				consumedThing.getEvent(eventName).observer().subscribe(
					next -> {
						LOG.info("Proxy "+id+" proxying event "+eventName+" - value: "+next);
						exposedProxy.getEvent(eventName).emit(next);
					},
					ex -> LOG.warning("Proxy "+id+" exception in event "+eventName+" subscriber: "+ex),
					() -> LOG.info("Proxy "+id+" event source for "+eventName+" closed")
				);
			}
		}
		
		return exposedProxy;
	}
}
