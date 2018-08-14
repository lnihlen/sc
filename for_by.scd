(
a = { forBy(1, 4, 1, _.postln) };
a.def.dumpByteCodes
)

(
c = { Integer.new(1).super.forBy(0, 4, 1, _.postln); };
c.def.dumpByteCodes;
c.value;
)

forBy(0, 4, 1, _.postln);

forBy(0, 4, 0.25, _.postln)

(
b = { forBy(1.0, 4, 0.33, _.postln) };
b.def.dumpByteCodes
)

(
c = { 0.0.forBy(4, 0.33, _.postln) };
c.def.dumpByteCodes;
)

(
d = { 0.0.asComplex };
d.def.dumpByteCodes;
)

forBy(0.0, 4, 0.25, postln("blarg"));