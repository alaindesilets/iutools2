package net.sf.hfst;

import java.util.Collection;

public abstract class Transducer {
    // Local change (see VENDORED.md): widened from package-private to public
    // so callers outside net.sf.hfst (org.iutools.morph.fst) can invoke it
    // polymorphically. Both concrete overrides were already public.
    public abstract Collection<String> analyze(String str) throws NoTokenizationException;
}
