COMPILATION ANALYSIS:
------------------------------

First, I ran:

strace javac *.java 2>javac-strace.txt
strace native-image Pigzj 2>native-strace.txt

to capture the output from strace into text files. I then ran:

cat javac-strace.txt | wc -l
cat native-strace.txt | wc -l

to count the number of system calls made for each of the two compilation steps, yielding 230 system calls for javac compilation, and 1499 for native-imaging. I also ran:

cat javac-strace.txt | sed 's/(.*//' | sort | uniq -c | sort -n -r
cat native-strace.txt | sed 's/(.*//' | sort | uniq -c | sort -n -r

which told me that the javac compilation is just another java program that spends all of its system calls opening up and running the JVM, which is why the system calls made by javac compilation are identical in terms of number and composition to that of Pigzj (later in the analysis). In contrast, native-image called:
    333 read
    222 fstat
    152 ioctl
    106 close
     93 openat
     82 access
     65 mmap
        ... and a lot more

By then running:

cat javac-strace.txt | grep mmap | cut -f2 -d, | paste -sd+ | bc
cat native-strace.txt | grep mmap | cut -f2 -d, | paste -sd+ | bc

I was able to sum all of the sizes of the chunks of data that were mapped with mmap, outputting 102,108,021 bytes, or about 102Mb of memory used during the native imaging, and 41.9Mb used during javac compilation (however, for javac, I don't know if that information is actually useful or relevant, as it is the exact same as the mmaps used in Pigzj as well, so it may still just be for initiating the JVM).

In terms of comparing the time used by each compilation step, compiling the Java program only took 0.638s in realtime with 1.304s in CPU time, while native-imaging took significantly longer, with 36.3s in realtime representing over 2m8s in CPU time.

RUNTIME ANALYSIS:
------------------------------

Using the given example of setting the input to /usr/local/cs/jdk-17.0.2/lib/modules, I obtained the following timestamps for the standard settings:

GZip:
     | Trial 1     Trial 2     Trial 3 | Average
------------------------------------------------
Real | 7.374s      7.633s      7.611s  | 7.539s
User | 7.164s      7.181s      7.187s  | 7.177s (Compression Ratio: 126,788,567 -> 43,479,040, 3:1 but least compressed)
Sys  | 0.064s      0.063s      0.074s  | 0.067s

PigZ:
     | Trial 1     Trial 2     Trial 3 | Average
------------------------------------------------
Real | 2.306s      2.278s      2.288s  | 2.290s
User | 7.060s      7.068s      7.085s  | 7.071s (Compression Ratio: 126,788,567 -> 43,351,345, about 3:1)
Sys  | 0.037s      0.044s      0.043s  | 0.041s

PigZJ (OpenJDK Compilation):
     | Trial 1     Trial 2     Trial 3 | Average
------------------------------------------------
Real | 2.499s      2.429s      2.427s  | 2.451s
User | 7.196s      7.232s      7.208s  | 7.212s (Compression Ratio: 126,788,567 -> 43,356,160, also about 3:1)
Sys  | 0.326s      0.286s      0.313s  | 0.308s

PigZJ (GraalVM Native-Image Compilation):
     | Trial 1     Trial 2     Trial 3 | Average
------------------------------------------------
Real | 2.674s      2.634s      2.659s  | 2.655s
User | 7.326s      7.342s      7.340s  | 7.336s (Compression Ratio: 126,788,567 -> 43,356,160, exact same as OpenJDK)
Sys  | 0.381s      0.402s      0.380s  | 0.387s

Looking at the results, PigZ and both executables for PigZJ massively outpace GZip in terms of real-time processing; PigZ takes about 5.3s less (30% of the time of GZip), while PigZJ takes about 4.9s less (35% as long as GZip). However, all of the programs take roughly the same amount of CPU time (User), with a little over 7 seconds for each. This could be from small sample sizes,  but it does seem like the Java implementations use roughly a fifth to two fifths of a second longer in CPU time than the other two.

In terms of compression ratios, 3:1 is pretty significant in terms of saved storage, and all of the programs have roughly the same compression ratio. PigZ barely edges out both of the PigZJ's, and interestingly GZip has the worst compression ratio of all. I would have expected that the applications where each block is processed seperately would have worse compression than the single threaded version, but the results seem to say otherwise. Also somewhat interestingly but I suppose unsurprisingly, the natively compiled and OpenJDK compiled versions of PigZJ have the exact same output, and thus an identical compression ratio. I suppose this means that native-image actually translates byte code into local machine code, instead of providing an alternate set of C libraries or something of the sort and a tool to translate Java code into C code, which could have potentially changed the actual output of the programs, although both would be valid implementations.

Now to try for a number of cores other than the default (4 cores is the default):

PigZ:
     | 1 Thread    2 Threads   8 Threads
----------------------------------------
Real |  7.415s      3.923s      2.378s
User |  6.946s      7.031s      7.070s
Sys  |  0.066s      0.087s      0.063s

PigZJ (OpenJDK Compilation):
     | 1 Thread    2 Threads   8 Threads
----------------------------------------
Real |  7.677s      4.125s      3.007s
User |  7.481s      7.437s      7.447s
Sys  |  0.344s      0.343s      0.396s

PigZJ (GraalVM Native-Image Compilation):
     | 1 Thread    2 Threads   8 Threads
----------------------------------------
Real |  7.442s      4.095s      3.264s
User |  7.577s      7.401s      7.402s
Sys  |  0.412s      0.423s      0.418s

The realtime performance for each program given a single thread is ever so slightly worse than GZip, but as soon as we go beyond a single thread we begin to outstrip GZip. We predicably follow the pattern of more processers -> less time, but once we begin creating more threads than there are CPU cores, we actually begin to lose performance. PigZ does a tenth of a second worse, and PigZJ and pigzj both do about 600ms worse when they each use 8 threads versus their default of 4 threads. This is likely due to the overhead of having to switch between threads on each processor as the CPU has to continuously change which threads are being actively run instead of allowing one thread per CPU to continue steadily. This thread switching wouldn't count towards CPU time which is why  we don't see much CPU time difference between each of the threads, but we do actually see an increase in Sys time for the 8 thread  tests vs. the 4 core tests; since Sys time refers to time spent making system calls in the kernel, this is likely when the thread  switching begins to make some time costs.

I am intrigued about why my programs perform significantly worse with the overhead of having unnecessary threads with respect to PigZ. One of the most palpable differences between PigZ and my implementation is the difference in Sys time calls, which could possibly be due to me writing each block one at a time to the output instead of doing it in one large chunk, but doing so did reduce my realtime as it allowed me to do some of the writing to standard output while other blocks were still compressing.

In terms of compression ratios, the compression ratios were unchanged by the number of cores used on each program. This makes sense, as the exact same tasks are executed, the only difference is the time at which they occur.

SYSTEM TRACES:
------------------------------
In order to save the outputs of the strace from each program, I wrote the following commands:

strace gzip <$input >gzip.gz 2>gzip-strace.txt
strace pigz <$input >pigz.gz 2>pigz-strace.txt
strace java Pigzj <$input >Pigzj.gz 2>Pigzj-strace.txt
strace ./pigzj <$input >pigzj.gz 2>pigzj-strace.txt

I then ran cat (strace-file) | wc -l to look at the number of system calls for each program:
GZip: 4079
PigZ: 1726
Pigzj (Open-JDK): 230
pigzj (native-imaged): 3424

I then decided to analyze the kinds of calls used by each strace:
cat <program_name>-strace.txt | sed 's/(.*//' | sort | uniq -c | sort -n -r
which produced output that looked like:

     62 openat
     39 stat
     29 mmap
     21 mprotect
     20 read
     12 fstat
     .... etc.

Starting off with my Open-JDK Pigzj, there were much less system calls than any other one of the programs. Very interestingly, there was actually the exact same amount and kind of system calls as the javac compilation. This makes me think that each of the calls for both programs were simply for initializing and running the JVM, and that any of the other system calls made within the JVM were not able to be detected by strace. In contrast, my native-imaged pigzj with 3424 calls had read tied with mmap as the most called system call, each dominating with 1009 instances, and write following behind with 970 write calls, which makes a little more sense to me. It is unsurprising that natively compiling Pigzj would cause pigzj to not open the java libraries, and the reappearance of system calls makes sense since pigzj no longer operates through the JVM and thus has visible system calls.

Looking at the standard programs, GZip was pretty amusing, with 3872 of its 4079 system calls just being read calls, each reading about 32kb at a time. With 166 write calls, there were only 41 system calls that were not either read or write. PigZ was dominated by 981 read calls and 623 futex calls (which to my understanding cause a thread to wait until a certain condition is met). Also interestingly, like Pigzj, PigZ had no write calls; unique amongst the programs native to Linux.

Clearly, the number of system calls does not have a strong correlation to amount of Sys time used, unless system calls made by internal Java libraries are not output to strace, as Pigzj has a fraction of the system calls but uses much more system time than GZip or PigZ. However, even though GZip had more system calls than pigzj, it still edged it out in terms of Sys time; perhaps read calls are more efficient than write calls, and so having fewer write calls helps it in terms of saving Sys time.

When looking at memory used by each program, I used the same command as in my compilation analysis to look at how much memory was mmaped. GZip only used 4.07Mb of space, PigZ used 57.6Mb of space, and pigzj used 215Mb. Pigzj did not produce useful information as it only printed the mmaps used in initializing the JVM. Keeping in mind that these calls are only how much memory was allocated over the entire runtime of the program and not necessarily how much memory was in use at peak time, it makes sense that pigzj uses the most memory over its runtime due to the overhead from using OOP. Luckily I, and I'm sure the developers of GZip and PigZ, free memory as the program runs.

FORESEEABLE PROBLEMS:
------------------------------

As discussed in the Time Analysis section, when we begin to add threads beyond the number of cores available on the CPU, we actually begin to lose some performance. Thus, naively continuing to request more threads from the programs will not continue to produce faster and faster compression times; at the end of the day, you are limited by how fast you can have all of the cores on your CPU running at the same time. Adding more threads than cores can be convenient for keeping track of multiple processes but can provide hits to performance.

In addition, as we continue to scale up the input, we can begin to run into RAM issues. As my current implementation stands, I have implemented some memory saving techniques. When a block is initialized, it stores its dictionary seperately from its uncompressed data, which initially causes more necessary memory. However, as soon as a block is compressed, it deletes both its own uncompressed data and the dictionary data of the previous block, allowing my program to continuously free memory as it continues to run and require more memory. My main thread also writes each block to standard output as soon as the previous block has been written to stdout, which lets me delete the compressed data as well. The only way to run out of RAM is if I continue to read in new values from System.in and allocate the necessary memory at a significantly faster rate than I am able to compress them and thus free the memory. Since reading is faster than compressing, this can certainly happen. While not implemented in my program, a possible solution to this would be to cause the main thread that is responsible for reading in values to wait whenever there are a certain number of uncompressed blocks, allowing the program to process and discard data before continuing to read in more.

