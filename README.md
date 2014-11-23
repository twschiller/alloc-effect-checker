alloc-effect-checker
====================

An effect system for enforcing allocation patterns. Implemented using the [Checker Framework](http://checkerframework.org).

__System Requirements__

1. The [Checker Framework](http://checkerframework.org)
2. Java 8, or the JSR 308 annotation tools (available on the Checker Framework website)

__How it Works__

The checker defines two method annotations `@MayAlloc` and `@NoAlloc` that specify whether or not a method may allocate memory.
The checker enforces that methods marked with `@NoAlloc` do no either directly or indirectly allocate memory.

__Running the Checker__

To run the checker, use `javac` with the `-processor com.toddschiller.checker.AllocEffectChecker` option. 
Remember to include the Allocation Checker and the Checker Framework on the Java classpath (e.g., using the `-cp` flag).

```
javac -processor com.toddschiller.checker.AllocEffectChecker MyFile.java
```

The run the checker in debug mode, use the `-Alint=debugSpew` flag.

__Example Output__

Example source:
```
@NoAlloc 
public void shouldWarn() {
  int x = mayAllocateMemory(); // an unannotated method
  int y = new Integer(3);
}
```

The corresponding checker output:

```
AllocationEffects.java:42: error: [call.invalid.alloc] Calling a method with MayAlloc effect from a context limited to NoAlloc effects.
            int i = new Integer(2);
                    ^
AllocationEffects.java:56: error: [call.invalid.alloc] Calling a method with MayAlloc effect from a context limited to NoAlloc effects.
        int x = mayAllocateMemory();
                                 ^
```

__Known Issues__

The checker does not currently detect allocations in the following cases:

* Reflection
* Static initializers (which run the first time a class is referenced during the execution)
* System methods and built-in operators, e.g., string concatenation `str1 + str2`


