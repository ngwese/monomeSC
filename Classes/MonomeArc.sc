/*

for information about monome devices:
monome.org

written by:
raja das, ezra buchla, dan derks, greg wuller

*/

MonomeArc {

	classvar seroscnet, discovery,
	prefixHandler,
	add, addCallback, addCallbackComplete,
	remove, removeCallback, removeCallbackComplete,
	portlst, prefixes, connectedDevices,
	quadDirty, ledQuads, redrawTimers;

	var prefixID, fpsVal, dvcID, encFunc, oscout; // instance variables

	*initClass {

		var sz;

		addCallback = nil;
		removeCallback = nil;
		portlst = List.new(0);
		connectedDevices = List.new(0);
		addCallbackComplete = List.new(0);
		removeCallbackComplete = List.new(0);
		prefixes = List.new(0);
		seroscnet = NetAddr.new("localhost", 12002);
		seroscnet.sendMsg("/serialosc/list", "127.0.0.1", NetAddr.localAddr.port);
		seroscnet.sendMsg("/serialosc/notify", "127.0.0.1", NetAddr.localAddr.port);

		quadDirty = Dictionary.new;
		ledQuads = Dictionary.new;
		redrawTimers = Dictionary.new;

		this.buildOSCResponders;

		ServerQuit.add({
			seroscnet.disconnect;
			add.free;
			discovery.free;
			remove.free;
			redrawTimers.do({arg dvc;
				redrawTimers[dvc].stop;
			});
		},\default);

	}

	*new { arg prefix, fps;
		prefix = case
		{prefix.isNil} {"/monome"}
		{prefix.notNil} {prefix.asString};

		fps = case
		{fps.isNil} {60}
		{fps.notNil} {fps.asFloat};

		^ super.new.init(prefix, fps);
	}

	*buildOSCResponders {
		var sz;

		add = OSCdef.newMatching(\monomeadd,
			{|msg, time, addr, recvPort|

				var portIDX;

				sz = switch (msg[2])
				{'monome arc'} {4}
				{'monome arc 2'} {2}
				{'monome arc 4'} {4};

				if( sz.notNil, { // if an arc
					if( portlst.includes(msg[3]) == false, {
						portlst.add(msg[3]);
						prefixes.add("/monome");
						connectedDevices.add(msg[1]);
						addCallbackComplete.add(false);
						removeCallbackComplete.add(false);
					});
					portIDX = portlst.detectIndex({arg item, i; item == msg[3]});
					if( addCallbackComplete[portIDX] == false,{
						("MonomeArc device added to port: "++msg[3]).postln;
						("MonomeArc serial: "++msg[1]).postln;
						("MonomeArc model: "++msg[2]).postln;
						addCallback.value(msg[1],msg[3],prefixes[portIDX]);
						addCallbackComplete[portIDX] = true;
						removeCallbackComplete[portIDX] = false;
					});
				}
				);

				seroscnet.sendMsg("/serialosc/notify", "127.0.0.1", NetAddr.localAddr.port);

		}, '/serialosc/add', seroscnet);

		remove = OSCdef.newMatching(\monomeremove,
			{|msg, time, addr, recvPort|
				var portIDX;

				sz = switch (msg[2])
				{'monome arc'} {4}
				{'monome arc 2'} {2}
				{'monome arc 4'} {4};

				if( sz.notNil, { // if an arc
					portIDX = portlst.detectIndex({arg item, i; item == msg[3]});
					if( portIDX.notNil, {
						if( removeCallbackComplete[portIDX] == false, {
							removeCallback.value(msg[2],msg[1],msg[3],prefixes[portIDX]);
							("MonomeArc device removed from port: "++msg[3]).postln;
							("MonomeArc serial: "++msg[1]).postln;
							("MonomeArc model: "++msg[2]).postln;
							addCallbackComplete[portIDX] = false;
							removeCallbackComplete[portIDX] = true;
						});
					});
				});

				seroscnet.sendMsg("/serialosc/notify", "127.0.0.1", NetAddr.localAddr.port);

		}, '/serialosc/remove', seroscnet);

		discovery = OSCdef.newMatching(\monomediscover,
			{|msg, time, addr, recvPort|

				var portIDX;

				sz = switch (msg[2])
				{'monome arc'} {4}
				{'monome arc 2'} {2}
				{'monome arc 4'} {4};

				if( sz.notNil, { // if an arc
					if( portlst.includes(msg[3]) == false, {
						portlst.add(msg[3]);
						connectedDevices.add(msg[1]);
						prefixes.add("/monome");
						addCallbackComplete.add(false);
						removeCallbackComplete.add(false);
						("MonomeArc device connected to port: "++msg[3]).postln;
						("MonomeArc serial: "++msg[1]).postln;
						("MonomeArc model: "++msg[2]).postln;
						portIDX = portlst.detectIndex({arg item, i; item == msg[3]});
						addCallback.value(msg[1],msg[3],prefixes[portIDX]);
					},{
						// ("arc already registered!!!").postln;
					});
				});

				seroscnet.sendMsg("/serialosc/notify", "127.0.0.1", NetAddr.localAddr.port);

		}, '/serialosc/device', seroscnet);

	}

	*refreshConnections {
		portlst.clear; connectedDevices.clear; prefixes.clear;
		seroscnet.sendMsg("/serialosc/list", "127.0.0.1", NetAddr.localAddr.port);
	}

	*getConnectedDevices {
		^connectedDevices;
	}

	*getPortList {
		^portlst;
	}

	*getPrefixes {
		^prefixes;
	}

	*setAddCallback { arg func;
		addCallback = nil;
		addCallback = func;
	}

	*setRemoveCallback { arg func;
		removeCallback = nil;
		removeCallback = func;
	}

	init { arg prefix_, fps_;
		prefixID = prefix_;
		fpsVal = fps_;
	}

	connectToPort { arg port;
		if( portlst.includes(port),{
			var idx = portlst.detectIndex({arg item, i; item == port});
			this.connect(idx);
		},{
			("no monome arc connected to specified port").warn;
		});
	}

	connectToSerial { arg serial;
		if( connectedDevices.includes(serial.asSymbol),{
			var idx = connectedDevices.detectIndex({arg item, i; item == serial});
			this.connect(idx);
		},{
			("no monome arc connected with specified serial").warn;
		});
	}

	connect { arg devicenum;
		if( devicenum == nil, {devicenum = 0});
		if( (portlst[devicenum].value).notNil, {

			var prefixDiscover;

			MonomeArc.buildOSCResponders;

			dvcID = devicenum;
			oscout = NetAddr.new("localhost", portlst[devicenum].value);
			Post << "MonomeArc: using device on port #" << portlst[devicenum].value << Char.nl;

			oscout.sendMsg(prefixID++"/ring/all", 0);

			prefixDiscover.free;
			prefixDiscover = OSCdef.newMatching(\monomeprefix,
				{|msg, time, addr, recvPort|
					prefixes[devicenum] = prefixID;
			}, '/sys/prefix', oscout);

			oscout.sendMsg("/sys/port", NetAddr.localAddr.port);
			oscout.sendMsg("/sys/prefix", prefixID);
			oscout.sendMsg("/sys/info");

			// collect individual LED messages into a 'map':
			quadDirty[dvcID] = Array.fill(8,{0});
			ledQuads[dvcID] = Array.fill(8,{Array.fill(64,{0})});

			redrawTimers[dvcID].stop;

			redrawTimers[dvcID] = Routine({
				var interval = 1/fpsVal,
				max = 4;

				loop {
					if( (portlst[devicenum].value).notNil,{
						for (0, max, {
							arg i;
							if(quadDirty[dvcID][i] != 0,
								{
									oscout.sendMsg(
										prefixID++"/ring/map",
										i,
										*ledQuads[dvcID][i]
									);
									quadDirty[dvcID][i] = 0;
								}
							);
						});
					});

					interval.yield;
				}

			});

			redrawTimers[dvcID].play();
			addCallbackComplete[dvcID] = false;
			addCallback.value(connectedDevices[dvcID], portlst[dvcID], prefixes[dvcID]);
			seroscnet.sendMsg("/serialosc/notify", "127.0.0.1", NetAddr.localAddr.port);
		},{
			("no monome arc detected at device slot " ++ devicenum).warn;
		});
	}

	usePort { arg portnum;
		dvcID = portlst.indexOf(portnum);
		oscout = NetAddr.new("localhost", portnum);
		Post << "MonomeArc: using device # " << dvcID << Char.nl;

		oscout.sendMsg("/sys/port", NetAddr.localAddr.port);
		oscout.sendMsg("/sys/prefix", prefixID);
	}

	port {
		if( dvcID.notNil, {
			^portlst[dvcID];
		},{
			^nil;
		});
	}

	serial {
		if( dvcID.notNil, {
			^connectedDevices[dvcID];
		},{
			^nil;
		});
	}

	prefix {
		if( dvcID.notNil, {
			^prefixes[dvcID];
		},{
			^nil;
		});
	}

	fps {
		if( dvcID.notNil, {
			^fpsVal
		},{
			^nil;
		});
	}

	dvcnum {
		^dvcID;
	}

	enc { arg func;
		encFunc = OSCdef.newMatching(
			("encFunc_" ++ dvcID).asSymbol,
			{ arg message, time, addr, recvPort;
				var n = message[1], d = message[2];
				if( dvcID.notNil,{
					if( this.port.value() == addr.port, {
						func.value(n,d);
					});
				});
			},
			prefixID++"/enc/delta"
		);
	}

	led { arg x,y,val;
		var offset;
		case
		// 64: quad 0 (top left)
		{(x < 8) && (y < 8)} {
			offset = (8*y)+x;
			ledQuads[dvcID][0][offset] = val;
			quadDirty[dvcID][0] = 1;
		}
		// 128: quad 1 (top right)
		{(x > 7) && (x < 16) && (y < 8)} {
			offset = (8*y)+(x-8);
			ledQuads[dvcID][1][offset] = val;
			quadDirty[dvcID][1] = 1;
		}
		// 256: quad 2 (bottom left)
		{(x < 8) && (y > 7) && (y < 16)} {
			offset = (8*(y-8))+x;
			ledQuads[dvcID][2][offset] = val;
			quadDirty[dvcID][2] = 1;
		}
		// 256: quad 3 (bottom right)
		{(x > 7) && (x < 16) && (y > 7) && (y < 16)} {
			offset = (8*(y-8))+(x-8);
			ledQuads[dvcID][3][offset] = val;
			quadDirty[dvcID][3] = 1;
		}
	}

	all { arg n, val;
		oscout.sendMsg(prefixID++"/ring/all", n, val);
	}

	// See here: http://monome.org/docs/tech:osc
	// if you need further explanation of the LED methods below
	ledset	{ arg n, x, val;
		oscout.sendMsg(prefixID++"/ring/set", n, x, val);
	}

	range	{ arg n, x1, x2, val;
		oscout.sendMsg(prefixID++"/ring/range", n, x1, x2, val);
	}

	cleanup {
		this.all(0);
		encFunc.free;
		oscout.disconnect;
	}

}