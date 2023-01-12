# This plugin helps with the player info packet bug

***This is not a fix, just make the effect less critical***

we still need to find why player's data is corrupted

to compile run `gradlew reobfJar`

the catched exception is `Internal Exception: java.lang.IndexOutOfBoundsException: readerIndex(18996) + length(1) exceeds writerIndex(18996): UnpooledByteBufAllocator$InstrumentedUnpooledUnsafeDirectByteBuf(ridx: 18996, widx: 18996, cap: 32768)`
