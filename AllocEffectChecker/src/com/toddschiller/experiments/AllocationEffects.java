package com.toddschiller.experiments;

import com.toddschiller.checker.qual.MayAlloc;
import com.toddschiller.checker.qual.NoAlloc;

/**
 * Demonstrates basic allocation checker functionality. See in-line comments for
 * descriptions of where checker warnings are triggered and why.
 * 
 * @author Todd Schiller
 */
public class AllocationEffects {

    public Integer mayAllocateMemory() {
        // No warning because by default methods are @MayAlloc
        return new Integer(1);
    }

    @NoAlloc
    public int noAllocateMemory() {
        // No warning because the method does not allocate memory
        return 42;
    }

    public static class SuperClass {
        @NoAlloc
        public void noAllocateMemory() {
            // NOP
        }

        @NoAlloc
        public void noAllocateMemory(int x) {
            // NOP
        }
    }

    public static class SubClass extends SuperClass {
        @Override
        public void noAllocateMemory() {
            // warning because the overridden method is @NoAlloc
            @SuppressWarnings("unused")
            int i = new Integer(2);
        }

        @Override
        @MayAlloc
        // warning because the overridden method is @NoAlloc
        public void noAllocateMemory(int x) {
            // NOP
        }
    }

    @NoAlloc
    public int shouldWarn() {
        // Warning because the called method is an allocation
        int x = mayAllocateMemory();
        
        // Warning because the new memory is allocated
        int y = new Integer(3);

        // Warning because the new memory is allocated
        int[] ys = new int[3];

        // No warning because we've suppressed it
        @SuppressWarnings("alloceffect")
        int z = new Integer(4);
        
        if (y != 3 && ys.length < 0 && z != 4) {
            // Warning because new memory is allocated for the exceptions
            // The warning is issued even though the condition will always be false
            throw new RuntimeException("Another allocation!");
        }
      
        return x;
    }

    public static void main(String[] args) {
        // NOP
    }
}
