package org.threadly.examples.prime;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Future;

import org.threadly.util.ExceptionUtils;

public class DumbTester implements PrimeProcessor {
  private static final BigInteger TWO = BigInteger.ONE.add(BigInteger.ONE);
  
  private final BigInteger n;
  private BigInteger factor;
  
  public DumbTester(BigInteger n) {
    this.n = n;
    factor = null;
  }
  
  @Override
  public BigInteger getFactor() {
    return factor;
  }
  
  @Override
  public boolean isPrime(Executor executor, int parallelLevel) throws InterruptedException {
    if (factor != null) {
      return false;
    }
    
    // quick check for even numbers
    if (n.mod(TWO).intValue() == 0) {
      factor = TWO;
      return false;
    }
    
    ExecutorCompletionService<BigInteger> ecs = new ExecutorCompletionService<BigInteger>(executor);
    List<Future<BigInteger>> futures = new ArrayList<Future<BigInteger>>(parallelLevel);
    
    // low numbers are not worth executing out
    BigInteger valuesPerThread = n.divide(BigInteger.valueOf(parallelLevel));
    if (valuesPerThread.intValue() < 10) {
      factor = new PrimeWorker(n, BigInteger.valueOf(3), n).call();
      
      return factor == null;
    }
    
    for (int i = 0; i < parallelLevel; i++) {
      futures.add(ecs.submit(new PrimeWorker(n, valuesPerThread.multiply(BigInteger.valueOf(i)), 
                                             valuesPerThread.multiply(BigInteger.valueOf(i + 1)))));
    }
    
    for (int i = 0; i < parallelLevel; i++) {
      Future<BigInteger> future = ecs.take();
      try {
        if (future.get() != null) {
          factor = future.get();
          Iterator<Future<BigInteger>> it = futures.iterator();
          while (it.hasNext()) {
            it.next().cancel(true);
          }
          return false;
        }
      } catch (ExecutionException e) {
        throw ExceptionUtils.makeRuntime(e);
      }
    }
    
    return true;
  }
  
  private static class PrimeWorker implements Callable<BigInteger> {
    private final BigInteger testVal;
    private final BigInteger startVal;
    private final BigInteger endVal;
    
    private PrimeWorker(BigInteger testVal, 
                        BigInteger startVal, 
                        BigInteger endVal) {
      this.testVal = testVal;
      if (startVal.mod(TWO).compareTo(BigInteger.ZERO) == 0) {
        startVal = startVal.add(BigInteger.ONE);
      }
      if (startVal.compareTo(BigInteger.ONE) == 0) {
        startVal = startVal.add(TWO);
      }
      this.startVal = startVal;
      if (endVal.compareTo(testVal) == 0) {
        endVal = endVal.subtract(BigInteger.ONE);
      }
      this.endVal = endVal;
    }
    
    @Override
    public BigInteger call() {
      for (BigInteger currentVal = startVal; 
           currentVal.compareTo(endVal) <= 0; 
           currentVal = currentVal.add(TWO)) {
        if (testVal.mod(currentVal).doubleValue() == 0) {
          return currentVal;
        }
      }
      
      return null;
    }
  }
}