(
// works as expected
[[1, 2, 3, 4], [5, 6, 7], [8]].lace(8);
[[1, 2, 3, 4], [5, 6, 7], [8]].lace();  // --> [1, 5, 8]

// this currently fails, comment in code is "works only on arrays"
// post-patch, returns [1, 2, 3, 4]
[1, 2, 3, 4].lace();

[1, 2, [], 4].lace;  // --> [1, 2, nil, 3]

// currently fails due to lacking argument
// post-patch, returns [ ]
[].lace();

// currently crashes sclang
// post-patch, returns [ nil ]
[[]].lace();

// Make sure slotCopy works for objects that aren't lists or arrays
[ { 1.0.rand }, [ 10, 20 ], [ 100, 200, 300 ] ].lace(7);
// --> [ a FunctionDef, 10, 100, a FunctionDef, 20, 200, a FunctionDef ]

["a", "b", "c", "d"].lace(4);
[["a", "b"], ["c", "d"]].lace;

TestArrayLace.run;
)