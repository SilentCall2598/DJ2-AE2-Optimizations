import java.util.*;


public final class EquivTest {


    static final class Tag { final String contents; Tag(String c) { contents = c; } }

    static final class Stack {
        final String item; final int damage; final Tag tag;
        Stack(String item, int damage, Tag tag) { this.item = item; this.damage = damage; this.tag = tag; }
        Key key() { return new Key(item, damage, tag == null ? null : tag.contents); }
    }

    static final class Key {
        final String item; final int damage; final String tag;
        Key(String item, int damage, String tag) { this.item = item; this.damage = damage; this.tag = tag; }
        public boolean equals(Object o) {
            if (!(o instanceof Key)) return false;
            Key k = (Key) o;
            return damage == k.damage && item.equals(k.item) && Objects.equals(tag, k.tag);
        }
        public int hashCode() { return Objects.hash(item, damage, tag); }
        public String toString() { return item + ":" + damage + (tag == null ? "" : "{" + tag + "}"); }
    }


    static final class AeStack {
        final Key key; long size; long requestable; boolean craftable;
        AeStack(Key key, long size) { this.key = key; this.size = size; }
        AeStack copy() { AeStack c = new AeStack(key, size); c.requestable = requestable; c.craftable = craftable; return c; }
        void add(AeStack o) { size += o.size; requestable += o.requestable; craftable |= o.craftable; }
        boolean meaningful() { return size != 0 || requestable > 0 || craftable; }
        public String toString() { return key + "x" + size; }
    }


    static final class ItemList implements Iterable<AeStack> {
        private final LinkedHashMap<Key, AeStack> records = new LinkedHashMap<Key, AeStack>();
        int version;

        void add(AeStack stack) {
            version++;
            if (stack == null) return;
            AeStack existing = records.get(stack.key);
            if (existing != null) existing.add(stack); else records.put(stack.key, stack.copy());
        }

        AeStack findPrecise(Key key) { return records.get(key); }


        public Iterator<AeStack> iterator() {
            final Iterator<Map.Entry<Key, AeStack>> parent = records.entrySet().iterator();
            return new Iterator<AeStack>() {
                AeStack next = seek();
                AeStack seek() {
                    while (parent.hasNext()) {
                        AeStack candidate = parent.next().getValue();
                        if (candidate.meaningful()) return candidate;
                        parent.remove();
                    }
                    return null;
                }
                public boolean hasNext() { return next != null; }
                public AeStack next() { AeStack r = next; next = seek(); return r; }
                public void remove() { throw new UnsupportedOperationException(); }
            };
        }

        Map<Key, Long> visible() {
            Map<Key, Long> out = new TreeMap<Key, Long>(new Comparator<Key>() {
                public int compare(Key a, Key b) { return a.toString().compareTo(b.toString()); }
            });
            for (AeStack s : this) out.put(s.key, s.size);
            return out;
        }
    }


    static final class Drawer {
        Stack prototype; int count; final int capacity; final boolean voiding; final boolean vending;
        Drawer(int capacity, boolean voiding, boolean vending) {
            this.capacity = capacity; this.voiding = voiding; this.vending = vending;
        }

        int advertisedCount() { return vending ? Integer.MAX_VALUE : count; }
        boolean isEmpty() { return prototype == null || (!vending && count <= 0); }
    }

    static final class Repository {
        final List<Drawer> drawers = new ArrayList<Drawer>();
        final boolean unstablePrototypes;
        Repository(boolean unstablePrototypes) { this.unstablePrototypes = unstablePrototypes; }

        List<Object[]> getAllItems() {
            List<Object[]> out = new ArrayList<Object[]>();
            for (Drawer d : drawers) {
                if (d.isEmpty()) continue;
                Stack proto = unstablePrototypes
                        ? new Stack(d.prototype.item, d.prototype.damage, d.prototype.tag)
                        : d.prototype;
                out.add(new Object[]{proto, d.advertisedCount()});
            }
            return out;
        }


        int insert(Key key, int amount) {
            for (Drawer d : drawers) {
                if (d.prototype == null || !d.prototype.key().equals(key)) continue;

                if (d.vending) return 0;
                int free = d.capacity - d.count;
                if (d.voiding) { d.count += Math.min(free, amount); return 0; }
                int accepted = Math.min(free, amount);
                d.count += accepted;
                return amount - accepted;
            }
            return amount;
        }

        int extract(Key key, int amount) {
            for (Drawer d : drawers) {
                if (d.prototype == null || !d.prototype.key().equals(key)) continue;
                if (d.vending) return amount;
                int taken = Math.min(d.count, amount);
                d.count -= taken;
                return taken;
            }
            return 0;
        }
    }


    interface Cache {
        List<AeStack> update();
        ItemList cached();
    }


    static final class StockCache implements Cache {
        final Repository repo; ItemList currentlyCached = new ItemList();
        StockCache(Repository repo) { this.repo = repo; }
        public ItemList cached() { return currentlyCached; }

        public List<AeStack> update() {
            List<AeStack> changes = new ArrayList<AeStack>();
            ItemList currentlyOnStorage = new ItemList();
            for (Object[] rec : repo.getAllItems()) {
                currentlyOnStorage.add(new AeStack(((Stack) rec[0]).key(), (Integer) rec[1]));
            }
            for (AeStack is : currentlyCached) is.size = -is.size;
            for (AeStack is : currentlyOnStorage) currentlyCached.add(is);
            for (AeStack is : currentlyCached) if (is.size != 0) changes.add(is);
            currentlyCached = currentlyOnStorage;
            return changes;
        }
    }


    static final class FastCache implements Cache {
        final Repository repo; ItemList currentlyCached = new ItemList();
        IdentityHashMap<Stack, Object[]> templates = new IdentityHashMap<Stack, Object[]>();
        int hits, lookups; boolean disabled;
        int prunes, pruned;
        int pollLookups, pollHits, judgedRebuilds;
        long judgedLookups, judgedHits;
        static final int WARMUP = 0, JUDGING = 1, CONFIRMED = 2, GIVEN_UP = 3;
        int stability = WARMUP;
        int allocatedProtoArrays;
        int conversions, templateCopies;

        FastCache(Repository repo) { this.repo = repo; }
        public ItemList cached() { return currentlyCached; }

        public List<AeStack> update() {
            List<Object[]> records = repo.getAllItems();
            int count = records.size();

            boolean needPruneSnapshot = !disabled && templates != null
                    && templates.size() > count * 2 + 16;

            Stack[] protos = needPruneSnapshot ? new Stack[count] : null;
            if (protos != null) allocatedProtoArrays++;

            ItemList currentlyOnStorage = new ItemList();
            pollLookups = 0; pollHits = 0;

            int index = 0;
            for (Object[] rec : records) {
                Stack prototype = (Stack) rec[0];
                long size = (Integer) rec[1];
                if (index < count && protos != null) protos[index] = prototype;
                index++;
                AeStack fresh;
                if (disabled) {
                    conversions++;
                    fresh = new AeStack(prototype.key(), 0);
                    fresh.size = size;
                } else {
                    AeStack template = template(prototype);
                    if (template == null) continue;
                    templateCopies++;
                    fresh = template.copy(); fresh.size = size;
                }
                currentlyOnStorage.add(fresh);
            }

            List<AeStack> changes = new ArrayList<AeStack>();
            for (AeStack is : currentlyCached) is.size = -is.size;
            for (AeStack is : currentlyOnStorage) currentlyCached.add(is);
            for (AeStack is : currentlyCached) if (is.size != 0) changes.add(is);
            currentlyCached = currentlyOnStorage;

            boolean aligned = index == count;
            if (needPruneSnapshot && aligned && protos != null) prune(protos, count);
            judgeIdentityStability();
            return changes;
        }

        AeStack template(Stack prototype) {
            lookups++; pollLookups++;
            Object[] entry = templates.get(prototype);
            if (entry != null
                    && entry[0].equals(prototype.item)
                    && ((Integer) entry[1]).intValue() == prototype.damage
                    && entry[2] == prototype.tag) {
                hits++; pollHits++;
                return (AeStack) entry[3];
            }
            conversions++;
            AeStack template = new AeStack(prototype.key(), 0);
            templates.put(prototype, new Object[]{prototype.item, prototype.damage, prototype.tag, template});
            return template;
        }


        void judgeIdentityStability() {
            if (stability == CONFIRMED || stability == GIVEN_UP) return;
            if (stability == WARMUP) { stability = JUDGING; return; }
            judgedRebuilds++; judgedLookups += pollLookups; judgedHits += pollHits;
            if (judgedRebuilds < 3 || judgedLookups < 512L) return;
            if (judgedHits * 4L >= judgedLookups) { stability = CONFIRMED; return; }
            stability = GIVEN_UP; disabled = true; templates = null;
        }

        void prune(Stack[] protos, int count) {
            if (templates == null || templates.size() <= count * 2 + 16) return;
            IdentityHashMap<Stack, Object[]> kept = new IdentityHashMap<Stack, Object[]>(count + 16);
            for (int i = 0; i < count; i++) {
                Stack prototype = protos[i];
                if (prototype == null) continue;
                Object[] entry = templates.get(prototype);
                if (entry != null) kept.put(prototype, entry);
            }
            prunes++; pruned += templates.size() - kept.size();
            templates = kept;
        }
    }


    static final class SteadyCounter {
        final Key key;
        AeStack representative;
        long value;
        long generation;
        SteadyCounter(Key key) { this.key = key; }
    }

    static final class SteadyCache implements Cache {
        final Repository repo; ItemList currentlyCached = new ItemList();
        IdentityHashMap<Stack, Object[]> templates = new IdentityHashMap<Stack, Object[]>();
        HashMap<Key, SteadyCounter> counters = new HashMap<Key, SteadyCounter>();
        long generationCounter;
        int hits, lookups; boolean templatesDisabled;
        boolean steadyDisabled;
        int prunes, pruned;
        int pollLookups, pollHits, judgedRebuilds;
        long judgedLookups, judgedHits;
        static final int WARMUP = 0, JUDGING = 1, CONFIRMED = 2, GIVEN_UP = 3;
        int stability = WARMUP;
        int steadyEligiblePolls, steadyServedPolls, steadyRecordsExamined, steadyCorrections,
                steadyAdditions, steadyRemovals, steadyInvariantFailures, steadyForcedFallbacks;

        SteadyCache(Repository repo) { this.repo = repo; }
        public ItemList cached() { return currentlyCached; }

        public List<AeStack> update() {
            List<Object[]> records = repo.getAllItems();
            int count = records.size();
            pollLookups = 0; pollHits = 0;

            boolean eligible = !templatesDisabled && !steadyDisabled && stability == CONFIRMED;
            if (eligible) {
                steadyEligiblePolls++;
                try {
                    List<AeStack> result = steadyUpdate(records, count);
                    judgeIdentityStability();
                    steadyServedPolls++;
                    return result;
                } catch (RuntimeException e) {
                    steadyInvariantFailures++;
                    steadyDisabled = true;
                    steadyForcedFallbacks++;
                    counters = new HashMap<Key, SteadyCounter>();
                }
            }

            boolean needPruneSnapshot = !templatesDisabled && templates != null
                    && templates.size() > count * 2 + 16;
            Stack[] protos = needPruneSnapshot ? new Stack[count] : null;

            ItemList currentlyOnStorage = new ItemList();
            int index = 0;
            for (Object[] rec : records) {
                Stack prototype = (Stack) rec[0];
                long size = (Integer) rec[1];
                if (index < count && protos != null) protos[index] = prototype;
                index++;
                AeStack fresh;
                if (templatesDisabled) {
                    fresh = new AeStack(prototype.key(), 0);
                    fresh.size = size;
                } else {
                    AeStack template = template(prototype);
                    if (template == null) continue;
                    fresh = template.copy(); fresh.size = size;
                }
                currentlyOnStorage.add(fresh);
            }

            List<AeStack> changes = new ArrayList<AeStack>();
            for (AeStack is : currentlyCached) is.size = -is.size;
            for (AeStack is : currentlyOnStorage) currentlyCached.add(is);
            for (AeStack is : currentlyCached) if (is.size != 0) changes.add(is);
            currentlyCached = currentlyOnStorage;

            boolean aligned = index == count;
            if (needPruneSnapshot && aligned && protos != null) prune(protos, count);
            judgeIdentityStability();
            return changes;
        }

        List<AeStack> steadyUpdate(List<Object[]> records, int count) {
            long generation = ++generationCounter;

            for (Object[] rec : records) {
                Stack prototype = (Stack) rec[0];
                long size = (Integer) rec[1];
                if (prototype == null || prototype.item == null) {
                    throw new IllegalStateException("malformed prototype");
                }
                AeStack template = template(prototype);
                if (template == null) {
                    throw new IllegalStateException("unconvertible prototype");
                }
                Key key = template.key;
                SteadyCounter counter = counters.get(key);
                if (counter == null) {
                    counter = new SteadyCounter(key);
                    counters.put(key, counter);
                }
                if (counter.generation != generation) {
                    counter.generation = generation;
                    counter.representative = template;
                    counter.value = size;
                } else {
                    counter.value += size;
                }
                steadyRecordsExamined++;
            }

            List<AeStack> changes = new ArrayList<AeStack>();
            for (Map.Entry<Key, SteadyCounter> e : counters.entrySet()) {
                SteadyCounter counter = e.getValue();
                if (counter.generation != generation) continue;
                AeStack existing = currentlyCached.findPrecise(counter.key);
                if (existing != null) {
                    long delta = counter.value - existing.size;
                    if (delta != 0) {
                        existing.size += delta;
                        AeStack d = existing.copy(); d.size = delta;
                        changes.add(d);
                        steadyCorrections++;
                    }
                } else {
                    AeStack fresh = counter.representative.copy();
                    fresh.size = counter.value;
                    currentlyCached.add(fresh);
                    changes.add(fresh.copy());
                    steadyAdditions++;
                }
            }

            for (AeStack existing : currentlyCached) {
                SteadyCounter counter = counters.get(existing.key);
                if ((counter == null || counter.generation != generation) && existing.size != 0) {
                    long old = existing.size;
                    existing.size = 0;
                    AeStack d = existing.copy(); d.size = -old;
                    changes.add(d);
                    steadyRemovals++;
                }
            }

            if (counters.size() > count * 2 + 16) {
                Iterator<Map.Entry<Key, SteadyCounter>> pruneIt = counters.entrySet().iterator();
                while (pruneIt.hasNext()) {
                    if (pruneIt.next().getValue().generation != generation) pruneIt.remove();
                }
            }

            return changes;
        }

        AeStack template(Stack prototype) {
            lookups++; pollLookups++;
            Object[] entry = templates.get(prototype);
            if (entry != null
                    && entry[0].equals(prototype.item)
                    && ((Integer) entry[1]).intValue() == prototype.damage
                    && entry[2] == prototype.tag) {
                hits++; pollHits++;
                return (AeStack) entry[3];
            }
            AeStack template = new AeStack(prototype.key(), 0);
            templates.put(prototype, new Object[]{prototype.item, prototype.damage, prototype.tag, template});
            return template;
        }

        void judgeIdentityStability() {
            if (stability == CONFIRMED || stability == GIVEN_UP) return;
            if (stability == WARMUP) { stability = JUDGING; return; }
            judgedRebuilds++; judgedLookups += pollLookups; judgedHits += pollHits;
            if (judgedRebuilds < 3 || judgedLookups < 512L) return;
            if (judgedHits * 4L >= judgedLookups) { stability = CONFIRMED; return; }
            stability = GIVEN_UP; templatesDisabled = true; templates = null;
            steadyDisabled = true; counters = new HashMap<Key, SteadyCounter>();
        }

        void prune(Stack[] protos, int count) {
            if (templates == null || templates.size() <= count * 2 + 16) return;
            IdentityHashMap<Stack, Object[]> kept = new IdentityHashMap<Stack, Object[]>(count + 16);
            for (int i = 0; i < count; i++) {
                Stack prototype = protos[i];
                if (prototype == null) continue;
                Object[] entry = templates.get(prototype);
                if (entry != null) kept.put(prototype, entry);
            }
            prunes++; pruned += templates.size() - kept.size();
            templates = kept;
        }
    }


    static void inject(Repository repo, ItemList cached, Key key, int amount, List<AeStack> posted) {
        int remaining = repo.insert(key, amount);
        if (remaining == amount) return;
        AeStack added = new AeStack(key, amount - remaining);
        cached.add(added);
        posted.add(added.copy());
    }

    static void extract(Repository repo, ItemList cached, Key key, int amount, List<AeStack> posted) {
        int extracted = repo.extract(key, amount);
        if (extracted <= 0) return;
        AeStack entry = cached.findPrecise(key);
        if (entry != null) entry.size -= extracted;
        AeStack diff = new AeStack(key, -extracted);
        posted.add(diff);
    }


    static String[] ITEMS = {"iron_ingot", "gold_ingot", "redstone", "enchanted_book", "cobblestone"};
    static Tag TAG_A = new Tag("{lvl:1}");
    static Tag TAG_B = new Tag("{lvl:2}");

    static Stack randomPrototype(Random r) {
        String item = ITEMS[r.nextInt(ITEMS.length)];
        int damage = r.nextInt(3);
        Tag tag = r.nextInt(4) == 0 ? (r.nextBoolean() ? TAG_A : TAG_B) : null;
        return new Stack(item, damage, tag);
    }

    static Repository buildWorld(Random r, boolean unstable, int drawers) {
        Repository repo = new Repository(unstable);
        for (int i = 0; i < drawers; i++) {
            Drawer d = new Drawer(2048, i % 7 == 0, i % 11 == 5);
            if (r.nextInt(4) != 0) { d.prototype = randomPrototype(r); d.count = r.nextInt(400); }
            repo.drawers.add(d);
        }
        return repo;
    }

    static List<String> normalize(List<AeStack> changes) {
        List<String> out = new ArrayList<String>();
        for (AeStack s : changes) out.add(s.key + "=" + s.size);
        Collections.sort(out);
        return out;
    }

    static int failures = 0;

    static void run(String name, long seed, boolean unstable, int steps) {
        Random rs = new Random(seed), rf = new Random(seed);
        Repository repoA = buildWorld(new Random(seed), unstable, 12);
        Repository repoB = buildWorld(new Random(seed), unstable, 12);
        StockCache stock = new StockCache(repoA);
        FastCache fast = new FastCache(repoB);

        stock.update(); fast.update();

        for (int step = 0; step < steps; step++) {
            int op = rs.nextInt(100); rf.nextInt(100);
            int drawerIndex = rs.nextInt(repoA.drawers.size()); rf.nextInt(repoB.drawers.size());
            int amount = 1 + rs.nextInt(300); rf.nextInt(300);
            Stack fresh = randomPrototype(rs); randomPrototype(rf);

            Drawer da = repoA.drawers.get(drawerIndex), db = repoB.drawers.get(drawerIndex);
            List<AeStack> postedA = new ArrayList<AeStack>(), postedB = new ArrayList<AeStack>();

            if (op < 25 && da.prototype != null) {
                extract(repoA, stock.cached(), da.prototype.key(), amount, postedA);
                extract(repoB, fast.cached(), db.prototype.key(), amount, postedB);
            } else if (op < 50 && da.prototype != null) {
                inject(repoA, stock.cached(), da.prototype.key(), amount, postedA);
                inject(repoB, fast.cached(), db.prototype.key(), amount, postedB);
            } else if (op < 70) {
                da.count = Math.max(0, da.count + (rs.nextBoolean() ? amount : -amount));
                db.count = Math.max(0, db.count + amount * 0);
                db.count = da.count;
            } else if (op < 80) {
                da.prototype = fresh; da.count = amount;
                db.prototype = new Stack(fresh.item, fresh.damage, fresh.tag); db.count = amount;
            } else if (op < 85) {
                da.count = 0; db.count = 0;
            } else if (op < 90) {
                da.prototype = null; da.count = 0;
                db.prototype = null; db.count = 0;
            }


            if (!normalize(postedA).equals(normalize(postedB))) {
                System.out.println("FAIL " + name + " step " + step + " MODULATE posts differ: "
                        + normalize(postedA) + " vs " + normalize(postedB));
                failures++; return;
            }

            List<AeStack> changesA = stock.update(), changesB = fast.update();
            if (!normalize(changesA).equals(normalize(changesB))) {
                System.out.println("FAIL " + name + " step " + step + " poll changes differ:\n  stock=" + normalize(changesA)
                        + "\n  fast =" + normalize(changesB));
                failures++; return;
            }
            if (!stock.cached().visible().equals(fast.cached().visible())) {
                System.out.println("FAIL " + name + " step " + step + " visible contents differ:\n  stock="
                        + stock.cached().visible() + "\n  fast =" + fast.cached().visible());
                failures++; return;
            }
        }
        System.out.println("pass  " + name + "  (" + steps + " steps, "
                + fast.hits + "/" + fast.lookups + " conversion cache hits, "
                + fast.prunes + " prunes dropping " + fast.pruned
                + (fast.disabled ? ", cache self-disabled" : "") + ")");
    }


    static void vendingRegression(int extractions, int amount) {
        String name = "vending regression (" + extractions + " x " + amount + ")";
        Repository repoA = new Repository(false), repoB = new Repository(false);
        Drawer da = new Drawer(2048, false, true), db = new Drawer(2048, false, true);
        Stack proto = new Stack("diamond", 0, null);
        da.prototype = proto; db.prototype = new Stack("diamond", 0, null);
        repoA.drawers.add(da); repoB.drawers.add(db);

        StockCache stock = new StockCache(repoA);
        FastCache fast = new FastCache(repoB);
        stock.update(); fast.update();

        Key key = proto.key();
        if (stock.cached().visible().get(key) != Integer.MAX_VALUE
                || fast.cached().visible().get(key) != Integer.MAX_VALUE) {
            System.out.println("FAIL " + name + ": caches did not prime to Integer.MAX_VALUE");
            failures++; return;
        }

        for (int i = 0; i < extractions; i++) {
            extract(repoA, stock.cached(), key, amount, new ArrayList<AeStack>());
            extract(repoB, fast.cached(), key, amount, new ArrayList<AeStack>());
        }

        long expectedShortfall = (long) Integer.MAX_VALUE - (long) extractions * amount;
        if (fast.cached().visible().get(key) != expectedShortfall) {
            System.out.println("FAIL " + name + ": cached stack was not decremented as AE2 would");
            failures++; return;
        }

        List<String> changesA = normalize(stock.update()), changesB = normalize(fast.update());

        if (!changesA.equals(changesB)) {
            System.out.println("FAIL " + name + ": corrections differ:\n  stock=" + changesA + "\n  fast =" + changesB);
            failures++; return;
        }
        if (fast.cached().visible().get(key) != Integer.MAX_VALUE) {
            System.out.println("FAIL " + name + ": cache was not restored to Integer.MAX_VALUE");
            failures++; return;
        }
        System.out.println("pass  " + name + "  (rebuild forced, posted " + changesB + ")");
    }


    static void largeStableRegression(int types, int polls) {
        String name = "large stable network (" + types + " types, " + polls + " polls)";
        Repository repoA = new Repository(false), repoB = new Repository(false);
        for (int i = 0; i < types; i++) {
            Drawer da = new Drawer(100000, false, false), db = new Drawer(100000, false, false);
            da.prototype = new Stack("stable_item_" + i, i % 3, null); da.count = 100 + i;
            db.prototype = new Stack("stable_item_" + i, i % 3, null); db.count = 100 + i;
            repoA.drawers.add(da); repoB.drawers.add(db);
        }
        StockCache stock = new StockCache(repoA);
        FastCache fast = new FastCache(repoB);

        stock.update(); fast.update();
        if (fast.pollHits != 0 || fast.pollLookups != types) {
            System.out.println("FAIL " + name + ": first poll should be all compulsory misses");
            failures++; return;
        }

        for (int poll = 1; poll < polls; poll++) {
            List<String> a = normalize(stock.update()), b = normalize(fast.update());
            if (!a.equals(b) || !stock.cached().visible().equals(fast.cached().visible())) {
                System.out.println("FAIL " + name + " poll " + poll + ": diverged from stock");
                failures++; return;
            }
            if (fast.disabled) {
                System.out.println("FAIL " + name + ": cache self-disabled at poll " + poll
                        + " on a perfectly stable repository");
                failures++; return;
            }
            if (fast.pollHits != types) {
                System.out.println("FAIL " + name + " poll " + poll + ": " + fast.pollHits
                        + " hits of " + types + " lookups; expected every one to hit");
                failures++; return;
            }
        }
        if (fast.prunes != 0) {
            System.out.println("FAIL " + name + ": pruned " + fast.prunes
                    + " times on a repository whose live set never changed");
            failures++; return;
        }
        if (fast.allocatedProtoArrays != 0) {
            System.out.println("FAIL " + name + ": allocated " + fast.allocatedProtoArrays
                    + " prototype arrays with no prune due");
            failures++; return;
        }
        System.out.println("pass  " + name + "  (" + fast.hits + "/" + fast.lookups
                + " hits, no self-disable, no prune, zero prototype arrays allocated)");
    }


    static void overflowRegression() {
        String name = "stability counter overflow boundary";
        Repository repo = new Repository(false);
        for (int i = 0; i < 200; i++) {
            Drawer d = new Drawer(100000, false, false);
            d.prototype = new Stack("stable_item_" + i, 0, null); d.count = 10 + i;
            repo.drawers.add(d);
        }
        FastCache fast = new FastCache(repo);
        fast.update();
        fast.update();


        fast.stability = FastCache.JUDGING;
        fast.judgedRebuilds = 2;
        fast.judgedLookups = 536870912L;
        fast.judgedHits = 536870912L;

        fast.update();

        if (fast.disabled) {
            System.out.println("FAIL " + name + ": a 100% hit rate disabled the cache");
            failures++; return;
        }
        if (fast.stability != FastCache.CONFIRMED) {
            System.out.println("FAIL " + name + ": expected the judgment to settle, state was " + fast.stability);
            failures++; return;
        }
        long parked = fast.judgedLookups;
        for (int i = 0; i < 20; i++) fast.update();
        if (fast.judgedLookups != parked) {
            System.out.println("FAIL " + name + ": kept accumulating after confirming (" + parked
                    + " -> " + fast.judgedLookups + ")");
            failures++; return;
        }
        System.out.println("pass  " + name + "  (confirmed and stopped counting at " + parked + " lookups)");
    }


    static void disabledFallbackRegression() {
        String name = "disabled fallback allocation";
        int types = 200;
        Repository repoA = new Repository(true), repoB = new Repository(true);
        for (int i = 0; i < types; i++) {
            Drawer da = new Drawer(100000, false, false), db = new Drawer(100000, false, false);
            da.prototype = new Stack("item_" + i, 0, null); da.count = 10 + i;
            db.prototype = new Stack("item_" + i, 0, null); db.count = 10 + i;
            repoA.drawers.add(da); repoB.drawers.add(db);
        }
        StockCache stock = new StockCache(repoA);
        FastCache fast = new FastCache(repoB);
        for (int i = 0; i < 8; i++) { stock.update(); fast.update(); }
        if (!fast.disabled) {
            System.out.println("FAIL " + name + ": expected the cache to give up on unstable identities");
            failures++; return;
        }

        int conversionsBefore = fast.conversions, copiesBefore = fast.templateCopies;
        int polls = 5;
        for (int i = 0; i < polls; i++) {
            List<String> a = normalize(stock.update()), b = normalize(fast.update());
            if (!a.equals(b)) {
                System.out.println("FAIL " + name + ": diverged from stock after disabling");
                failures++; return;
            }
        }
        int conversions = fast.conversions - conversionsBefore;
        int copies = fast.templateCopies - copiesBefore;
        if (conversions != types * polls) {
            System.out.println("FAIL " + name + ": " + conversions + " conversions for "
                    + (types * polls) + " records; expected one each");
            failures++; return;
        }
        if (copies != 0) {
            System.out.println("FAIL " + name + ": " + copies + " template copies while disabled; expected none");
            failures++; return;
        }
        System.out.println("pass  " + name + "  (" + conversions + " conversions, 0 extra copies over "
                + polls + " polls)");
    }

    static List<AeStack> mirrorUpdateWithMalformedGuard(List<Object[]> records, ItemList currentlyCached) {
        ItemList currentlyOnStorage = new ItemList();
        for (Object[] rec : records) {
            Stack prototype = (Stack) rec[0];
            if (prototype == null || prototype.item == null) {
                return null;
            }
            currentlyOnStorage.add(new AeStack(prototype.key(), (Integer) rec[1]));
        }
        List<AeStack> changes = new ArrayList<AeStack>();
        for (AeStack is : currentlyCached) is.size = -is.size;
        for (AeStack is : currentlyOnStorage) currentlyCached.add(is);
        for (AeStack is : currentlyCached) if (is.size != 0) changes.add(is);
        return changes;
    }

    static void malformedRecordFallbackRegression() {
        String nullPrototype = "null prototype falls open without mutating the cache";
        ItemList cachedA = new ItemList();
        cachedA.add(new AeStack(new Key("iron_ingot", 0, null), 5));
        Map<Key, Long> beforeA = cachedA.visible();
        List<Object[]> recordsA = new ArrayList<Object[]>();
        recordsA.add(new Object[]{new Stack("gold_ingot", 0, null), 3});
        recordsA.add(new Object[]{null, 1});
        List<AeStack> resultA = mirrorUpdateWithMalformedGuard(recordsA, cachedA);
        if (resultA != null) {
            System.out.println("FAIL " + nullPrototype + ": expected a null (fall-open) signal, got a result");
            failures++;
        } else if (!cachedA.visible().equals(beforeA)) {
            System.out.println("FAIL " + nullPrototype + ": the cache was mutated before the malformed record was detected");
            failures++;
        } else {
            System.out.println("pass  " + nullPrototype);
        }

        String unconvertible = "unconvertible prototype falls open without mutating the cache";
        ItemList cachedB = new ItemList();
        cachedB.add(new AeStack(new Key("iron_ingot", 0, null), 5));
        Map<Key, Long> beforeB = cachedB.visible();
        List<Object[]> recordsB = new ArrayList<Object[]>();
        recordsB.add(new Object[]{new Stack("gold_ingot", 0, null), 3});
        recordsB.add(new Object[]{new Stack(null, 0, null), 1});
        List<AeStack> resultB = mirrorUpdateWithMalformedGuard(recordsB, cachedB);
        if (resultB != null) {
            System.out.println("FAIL " + unconvertible + ": expected a null (fall-open) signal, got a result");
            failures++;
        } else if (!cachedB.visible().equals(beforeB)) {
            System.out.println("FAIL " + unconvertible + ": the cache was mutated before the malformed record was detected");
            failures++;
        } else {
            System.out.println("pass  " + unconvertible);
        }
    }

    static SteadyCache confirmedSteadyCache(Repository repo) {
        SteadyCache cache = new SteadyCache(repo);
        for (int i = 0; i < 3; i++) {
            cache.update();
        }
        while (cache.judgedLookups < 512L) {
            cache.judgedRebuilds++;
            cache.judgedLookups += 512L;
            cache.judgedHits += 512L;
        }
        cache.judgeIdentityStability();
        return cache;
    }

    static void steadyWorkedExamples() {
        {
            String name = "worked example: normal MODULATE extraction leaves nothing to correct";
            Repository repo = new Repository(false);
            Drawer d = new Drawer(2048, false, false);
            Stack proto = new Stack("iron_ingot", 0, null);
            d.prototype = proto; d.count = 100;
            repo.drawers.add(d);
            SteadyCache cache = confirmedSteadyCache(repo);
            cache.update();

            List<AeStack> posted = new ArrayList<AeStack>();
            extract(repo, cache.cached(), proto.key(), 10, posted);
            if (d.count != 90 || cache.cached().visible().get(proto.key()) != 90L) {
                System.out.println("FAIL " + name + ": repository/cache did not both move to 90");
                failures++;
            } else {
                List<AeStack> changes = cache.update();
                if (!changes.isEmpty()) {
                    System.out.println("FAIL " + name + ": expected zero changes, got " + normalize(changes));
                    failures++;
                } else if (cache.cached().visible().get(proto.key()) != 90L) {
                    System.out.println("FAIL " + name + ": cache drifted off 90 after the poll");
                    failures++;
                } else {
                    System.out.println("pass  " + name);
                }
            }
        }

        {
            String name = "worked example: external drawer mutation reports the exact -5 correction";
            Repository repo = new Repository(false);
            Drawer d = new Drawer(2048, false, false);
            Stack proto = new Stack("gold_ingot", 0, null);
            d.prototype = proto; d.count = 100;
            repo.drawers.add(d);
            SteadyCache cache = confirmedSteadyCache(repo);
            cache.update();

            d.count = 95;
            List<AeStack> changes = cache.update();
            Map<Key, Long> byKey = new java.util.HashMap<Key, Long>();
            for (AeStack s : changes) byKey.put(s.key, s.size);
            if (changes.size() != 1 || byKey.get(proto.key()) != -5L) {
                System.out.println("FAIL " + name + ": expected exactly one -5 change, got " + normalize(changes));
                failures++;
            } else if (cache.cached().visible().get(proto.key()) != 95L) {
                System.out.println("FAIL " + name + ": cache was not corrected to 95");
                failures++;
            } else {
                System.out.println("pass  " + name);
            }
        }

        {
            String name = "worked example: Creative Vending corrects back to Integer.MAX_VALUE";
            Repository repo = new Repository(false);
            Drawer d = new Drawer(2048, false, true);
            Stack proto = new Stack("diamond", 0, null);
            d.prototype = proto;
            repo.drawers.add(d);
            SteadyCache cache = confirmedSteadyCache(repo);
            cache.update();
            if (cache.cached().visible().get(proto.key()) != (long) Integer.MAX_VALUE) {
                System.out.println("FAIL " + name + ": cache did not prime to Integer.MAX_VALUE");
                failures++;
            } else {
                extract(repo, cache.cached(), proto.key(), 64, new ArrayList<AeStack>());
                long afterExtract = cache.cached().visible().get(proto.key());
                if (afterExtract != (long) Integer.MAX_VALUE - 64L) {
                    System.out.println("FAIL " + name + ": cache was not decremented as AE2 would");
                    failures++;
                } else {
                    List<AeStack> changes = cache.update();
                    Map<Key, Long> byKey = new java.util.HashMap<Key, Long>();
                    for (AeStack s : changes) byKey.put(s.key, s.size);
                    if (changes.size() != 1 || byKey.get(proto.key()) != 64L) {
                        System.out.println("FAIL " + name + ": expected exactly one +64 correction, got "
                                + normalize(changes));
                        failures++;
                    } else if (cache.cached().visible().get(proto.key()) != (long) Integer.MAX_VALUE) {
                        System.out.println("FAIL " + name + ": cache was not restored to Integer.MAX_VALUE");
                        failures++;
                    } else {
                        System.out.println("pass  " + name);
                    }
                }
            }
        }

        {
            String name = "worked example: full void drawer insertion is repaired on the next poll";
            Repository repo = new Repository(false);
            Drawer d = new Drawer(2048, true, false);
            Stack proto = new Stack("cobblestone", 0, null);
            d.prototype = proto; d.count = d.capacity;
            repo.drawers.add(d);
            SteadyCache cache = confirmedSteadyCache(repo);
            cache.update();

            List<AeStack> posted = new ArrayList<AeStack>();
            inject(repo, cache.cached(), proto.key(), 32, posted);
            if (d.count != d.capacity) {
                System.out.println("FAIL " + name + ": a full voiding drawer should not have actually stored "
                        + "the new insertion");
                failures++;
            } else if (cache.cached().visible().get(proto.key()) != (long) d.capacity + 32L) {
                System.out.println("FAIL " + name + ": cache should have phantom-gained 32 from the injectItems post");
                failures++;
            } else {
                List<AeStack> changes = cache.update();
                Map<Key, Long> byKey = new java.util.HashMap<Key, Long>();
                for (AeStack s : changes) byKey.put(s.key, s.size);
                if (changes.size() != 1 || byKey.get(proto.key()) != -32L) {
                    System.out.println("FAIL " + name + ": expected exactly one -32 correction, got "
                            + normalize(changes));
                    failures++;
                } else if (cache.cached().visible().get(proto.key()) != (long) d.capacity) {
                    System.out.println("FAIL " + name + ": phantom inventory was not fully repaired back to capacity");
                    failures++;
                } else {
                    System.out.println("pass  " + name);
                }
            }
        }
    }

    static void runSteady(String name, long seed, boolean unstable, int steps) {
        Random rs = new Random(seed), rf = new Random(seed);
        Repository repoA = buildWorld(new Random(seed), unstable, 12);
        Repository repoB = buildWorld(new Random(seed), unstable, 12);
        StockCache stock = new StockCache(repoA);
        SteadyCache steady = new SteadyCache(repoB);

        stock.update(); steady.update();

        for (int step = 0; step < steps; step++) {
            int op = rs.nextInt(100); rf.nextInt(100);
            int drawerIndex = rs.nextInt(repoA.drawers.size()); rf.nextInt(repoB.drawers.size());
            int amount = 1 + rs.nextInt(300); rf.nextInt(300);
            Stack fresh = randomPrototype(rs); randomPrototype(rf);

            Drawer da = repoA.drawers.get(drawerIndex), db = repoB.drawers.get(drawerIndex);
            List<AeStack> postedA = new ArrayList<AeStack>(), postedB = new ArrayList<AeStack>();

            if (op < 25 && da.prototype != null) {
                extract(repoA, stock.cached(), da.prototype.key(), amount, postedA);
                extract(repoB, steady.cached(), db.prototype.key(), amount, postedB);
            } else if (op < 50 && da.prototype != null) {
                inject(repoA, stock.cached(), da.prototype.key(), amount, postedA);
                inject(repoB, steady.cached(), db.prototype.key(), amount, postedB);
            } else if (op < 70) {
                da.count = Math.max(0, da.count + (rs.nextBoolean() ? amount : -amount));
                db.count = da.count;
            } else if (op < 80) {
                da.prototype = fresh; da.count = amount;
                db.prototype = new Stack(fresh.item, fresh.damage, fresh.tag); db.count = amount;
            } else if (op < 85) {
                da.count = 0; db.count = 0;
            } else if (op < 90) {
                da.prototype = null; da.count = 0;
                db.prototype = null; db.count = 0;
            }

            if (!normalize(postedA).equals(normalize(postedB))) {
                System.out.println("FAIL " + name + " step " + step + " MODULATE posts differ: "
                        + normalize(postedA) + " vs " + normalize(postedB));
                failures++; return;
            }

            List<AeStack> changesA = stock.update(), changesB = steady.update();
            if (!normalize(changesA).equals(normalize(changesB))) {
                System.out.println("FAIL " + name + " step " + step + " poll changes differ:\n  stock=" + normalize(changesA)
                        + "\n  steady=" + normalize(changesB));
                failures++; return;
            }
            if (!stock.cached().visible().equals(steady.cached().visible())) {
                System.out.println("FAIL " + name + " step " + step + " visible contents differ:\n  stock="
                        + stock.cached().visible() + "\n  steady=" + steady.cached().visible());
                failures++; return;
            }
        }

        if (!unstable && steady.stability == SteadyCache.CONFIRMED && steady.steadyServedPolls == 0) {
            System.out.println("FAIL " + name + ": stability confirmed but the steady-state path was never served");
            failures++; return;
        }
        if (steady.steadyInvariantFailures != 0) {
            System.out.println("FAIL " + name + ": " + steady.steadyInvariantFailures
                    + " invariant failure(s) on a well-formed world");
            failures++; return;
        }

        System.out.println("pass  " + name + "  (" + steps + " steps, " + steady.steadyServedPolls
                + "/" + steady.steadyEligiblePolls + " steady polls served, "
                + steady.steadyCorrections + " corrections, " + steady.steadyAdditions + " additions, "
                + steady.steadyRemovals + " removals"
                + (steady.templatesDisabled ? ", cache self-disabled" : "") + ")");
    }

    static void steadyLargeStableRegression(int types, int polls) {
        String name = "steady large stable network (" + types + " types, " + polls + " polls)";
        Repository repoA = new Repository(false), repoB = new Repository(false);
        for (int i = 0; i < types; i++) {
            Drawer da = new Drawer(100000, false, false), db = new Drawer(100000, false, false);
            da.prototype = new Stack("stable_item_" + i, i % 3, null); da.count = 100 + i;
            db.prototype = new Stack("stable_item_" + i, i % 3, null); db.count = 100 + i;
            repoA.drawers.add(da); repoB.drawers.add(db);
        }
        StockCache stock = new StockCache(repoA);
        SteadyCache steady = new SteadyCache(repoB);
        stock.update(); steady.update();

        for (int poll = 1; poll < polls; poll++) {
            List<String> a = normalize(stock.update()), b = normalize(steady.update());
            if (!a.equals(b) || !stock.cached().visible().equals(steady.cached().visible())) {
                System.out.println("FAIL " + name + " poll " + poll + ": diverged from stock");
                failures++; return;
            }
        }
        if (steady.templatesDisabled) {
            System.out.println("FAIL " + name + ": cache self-disabled on a perfectly stable repository");
            failures++; return;
        }
        if (steady.steadyServedPolls == 0) {
            System.out.println("FAIL " + name + ": steady-state path was never served on a stable network");
            failures++; return;
        }
        if (steady.steadyCorrections != 0 || steady.steadyAdditions != 0 || steady.steadyRemovals != 0) {
            System.out.println("FAIL " + name + ": " + steady.steadyCorrections + " corrections, "
                    + steady.steadyAdditions + " additions, " + steady.steadyRemovals
                    + " removals on a repository whose live set never changed");
            failures++; return;
        }
        System.out.println("pass  " + name + "  (" + steady.steadyServedPolls + "/" + steady.steadyEligiblePolls
                + " steady polls served, zero spurious corrections/additions/removals)");
    }

    static void steadyVendingRegression(int extractions, int amount) {
        String name = "steady vending regression (" + extractions + " x " + amount + ")";
        Repository repoA = new Repository(false), repoB = new Repository(false);
        Drawer da = new Drawer(2048, false, true), db = new Drawer(2048, false, true);
        Stack proto = new Stack("diamond", 0, null);
        da.prototype = proto; db.prototype = new Stack("diamond", 0, null);
        repoA.drawers.add(da); repoB.drawers.add(db);

        StockCache stock = new StockCache(repoA);
        SteadyCache steady = confirmedSteadyCache(repoB);
        stock.update(); steady.update();

        Key key = proto.key();
        for (int i = 0; i < extractions; i++) {
            extract(repoA, stock.cached(), key, amount, new ArrayList<AeStack>());
            extract(repoB, steady.cached(), key, amount, new ArrayList<AeStack>());
        }

        List<String> changesA = normalize(stock.update()), changesB = normalize(steady.update());
        if (!changesA.equals(changesB)) {
            System.out.println("FAIL " + name + ": corrections differ:\n  stock=" + changesA + "\n  steady=" + changesB);
            failures++; return;
        }
        if (steady.cached().visible().get(key) != (long) Integer.MAX_VALUE) {
            System.out.println("FAIL " + name + ": cache was not restored to Integer.MAX_VALUE");
            failures++; return;
        }
        if (steady.steadyServedPolls == 0) {
            System.out.println("FAIL " + name + ": steady-state path was never actually exercised");
            failures++; return;
        }
        System.out.println("pass  " + name + "  (steady state served, posted " + changesB + ")");
    }

    static void steadyLongIdleRegression() {
        String name = "steady long idle run";
        Repository repo = new Repository(false);
        for (int i = 0; i < 40; i++) {
            Drawer d = new Drawer(100000, false, false);
            d.prototype = new Stack("idle_item_" + i, 0, null); d.count = 50 + i;
            repo.drawers.add(d);
        }
        SteadyCache steady = confirmedSteadyCache(repo);
        steady.update();
        long servedBefore = steady.steadyServedPolls;
        for (int i = 0; i < 500; i++) {
            List<AeStack> changes = steady.update();
            if (!changes.isEmpty()) {
                System.out.println("FAIL " + name + ": unexpected change on a perfectly idle network at poll " + i
                        + ": " + normalize(changes));
                failures++; return;
            }
        }
        if (steady.steadyServedPolls == servedBefore) {
            System.out.println("FAIL " + name + ": steady state was never served across 500 idle polls");
            failures++; return;
        }
        if (steady.counters.size() > 40 * 2 + 16) {
            System.out.println("FAIL " + name + ": steady counters grew unbounded over an idle run ("
                    + steady.counters.size() + " entries for 40 stable types)");
            failures++; return;
        }
        System.out.println("pass  " + name + "  (500 idle polls, zero changes, counters bounded at "
                + steady.counters.size() + ")");
    }

    public static void main(String[] args) {
        overflowRegression();
        disabledFallbackRegression();
        malformedRecordFallbackRegression();
        largeStableRegression(512, 10);
        largeStableRegression(1000, 10);
        vendingRegression(1, 64);
        vendingRegression(5, 64);
        vendingRegression(100, 999);

        steadyWorkedExamples();
        steadyLargeStableRegression(512, 10);
        steadyLargeStableRegression(1000, 10);
        steadyVendingRegression(1, 64);
        steadyVendingRegression(5, 64);
        steadyVendingRegression(100, 999);
        steadyLongIdleRegression();

        for (long seed = 1; seed <= 20; seed++) {
            run("stable   seed" + seed, seed, false, 4000);
        }
        for (long seed = 1; seed <= 5; seed++) {
            run("unstable seed" + seed, seed, true, 4000);
        }
        for (long seed = 1; seed <= 20; seed++) {
            runSteady("steady stable   seed" + seed, seed, false, 4000);
        }
        for (long seed = 1; seed <= 5; seed++) {
            runSteady("steady unstable seed" + seed, seed, true, 4000);
        }
        String[] few = ITEMS;
        String[] many = new String[120];
        for (int i = 0; i < many.length; i++) many[i] = "churn_item_" + i;
        ITEMS = many;
        for (long seed = 1; seed <= 5; seed++) {
            run("churn    seed" + seed, seed, false, 4000);
        }
        for (long seed = 1; seed <= 5; seed++) {
            runSteady("steady churn    seed" + seed, seed, false, 4000);
        }
        ITEMS = few;
        System.out.println(failures == 0 ? "\nALL PASS" : "\n" + failures + " FAILURES");
        if (failures != 0) System.exit(1);
    }
}
