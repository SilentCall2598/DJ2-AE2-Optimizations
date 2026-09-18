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
        final boolean skipUnchanged;
        IdentityHashMap<Stack, Object[]> templates = new IdentityHashMap<Stack, Object[]>();
        Stack[] snapshotProtos; long[] snapshotCounts; int snapshotLength;
        int cachedListVersion; boolean haveListVersion;
        AeStack[] cachedRefs; long[] cachedSizes; int cachedEntryCount;
        int hits, lookups; boolean disabled;
        int skips, prunes, pruned, clears;
        int pollLookups, pollHits, judgedRebuilds;
        long judgedLookups, judgedHits;
        static final int WARMUP = 0, JUDGING = 1, CONFIRMED = 2, GIVEN_UP = 3;
        int stability = WARMUP;
        int allocatedProtoArrays, allocatedCountArrays;
        int conversions, templateCopies;

        FastCache(Repository repo, boolean skipUnchanged) { this.repo = repo; this.skipUnchanged = skipUnchanged; }
        public ItemList cached() { return currentlyCached; }

        public List<AeStack> update() {
            List<Object[]> records = repo.getAllItems();
            int count = records.size();

            boolean needSkipSnapshot = skipUnchanged && !disabled;

            if (needSkipSnapshot && repositoryUnchanged(count, records)
                    && cachedListUnchanged() && cachedContentsUnchanged()) {
                skips++;
                return Collections.emptyList();
            }

            boolean needPruneSnapshot = !disabled && templates != null
                    && templates.size() > count * 2 + 16;

            Stack[] protos = (needSkipSnapshot || needPruneSnapshot) ? new Stack[count] : null;
            long[] counts = needSkipSnapshot ? new long[count] : null;
            if (protos != null) allocatedProtoArrays++;
            if (counts != null) allocatedCountArrays++;

            ItemList currentlyOnStorage = new ItemList();
            pollLookups = 0; pollHits = 0;

            int index = 0;
            for (Object[] rec : records) {
                Stack prototype = (Stack) rec[0];
                long size = (Integer) rec[1];
                if (index < count) {
                    if (protos != null) protos[index] = prototype;
                    if (counts != null) counts[index] = size;
                }
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
            if (needSkipSnapshot && aligned) {
                snapshotProtos = protos; snapshotCounts = counts; snapshotLength = count;
                cachedListVersion = currentlyOnStorage.version; haveListVersion = true;
                rememberCacheContents(currentlyOnStorage, count);
            } else {
                forgetSkipState();
            }
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
            stability = GIVEN_UP; disabled = true; templates = null; forgetSkipState();
        }

        void forgetSkipState() {
            snapshotProtos = null; snapshotCounts = null; snapshotLength = 0;
            haveListVersion = false; cachedRefs = null; cachedSizes = null; cachedEntryCount = 0;
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

        boolean repositoryUnchanged(int count, List<Object[]> records) {
            if (snapshotProtos == null || snapshotLength != count || snapshotProtos.length < count) return false;
            int index = 0;
            for (Object[] rec : records) {
                if (index >= count || rec[0] != snapshotProtos[index]
                        || ((Integer) rec[1]).longValue() != snapshotCounts[index]) return false;
                index++;
            }
            return index == count;
        }

        void rememberCacheContents(ItemList list, int capacity) {
            AeStack[] refs = new AeStack[capacity];
            long[] sizes = new long[capacity];
            int index = 0;
            for (AeStack is : list) {
                if (index >= capacity) { cachedRefs = null; cachedEntryCount = 0; return; }
                refs[index] = is; sizes[index] = is.size; index++;
            }
            cachedRefs = refs; cachedSizes = sizes; cachedEntryCount = index;
        }

        boolean cachedContentsUnchanged() {
            if (cachedRefs == null) return false;
            for (int i = 0; i < cachedEntryCount; i++) {
                if (cachedRefs[i].size != cachedSizes[i]) return false;
            }
            return true;
        }

        boolean cachedListUnchanged() {
            return haveListVersion && currentlyCached.version == cachedListVersion;
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

    static List<String> normalise(List<AeStack> changes) {
        List<String> out = new ArrayList<String>();
        for (AeStack s : changes) out.add(s.key + "=" + s.size);
        Collections.sort(out);
        return out;
    }

    static int failures = 0;

    static void run(String name, long seed, boolean skipUnchanged, boolean unstable, int steps) {
        Random rs = new Random(seed), rf = new Random(seed);
        Repository repoA = buildWorld(new Random(seed), unstable, 12);
        Repository repoB = buildWorld(new Random(seed), unstable, 12);
        StockCache stock = new StockCache(repoA);
        FastCache fast = new FastCache(repoB, skipUnchanged);

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


            if (!normalise(postedA).equals(normalise(postedB))) {
                System.out.println("FAIL " + name + " step " + step + " MODULATE posts differ: "
                        + normalise(postedA) + " vs " + normalise(postedB));
                failures++; return;
            }

            List<AeStack> changesA = stock.update(), changesB = fast.update();
            if (!normalise(changesA).equals(normalise(changesB))) {
                System.out.println("FAIL " + name + " step " + step + " poll changes differ:\n  stock=" + normalise(changesA)
                        + "\n  fast =" + normalise(changesB));
                failures++; return;
            }
            if (!stock.cached().visible().equals(fast.cached().visible())) {
                System.out.println("FAIL " + name + " step " + step + " visible contents differ:\n  stock="
                        + stock.cached().visible() + "\n  fast =" + fast.cached().visible());
                failures++; return;
            }
        }
        System.out.println("pass  " + name + "  (" + steps + " steps, " + fast.skips + " polls skipped, "
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
        FastCache fast = new FastCache(repoB, true);
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

        int skipsBefore = fast.skips;
        List<String> changesA = normalise(stock.update()), changesB = normalise(fast.update());

        if (fast.skips != skipsBefore) {
            System.out.println("FAIL " + name + ": the poll was skipped, leaving the network short by "
                    + (long) extractions * amount);
            failures++; return;
        }
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
        FastCache fast = new FastCache(repoB, false);

        stock.update(); fast.update();
        if (fast.pollHits != 0 || fast.pollLookups != types) {
            System.out.println("FAIL " + name + ": first poll should be all compulsory misses");
            failures++; return;
        }

        for (int poll = 1; poll < polls; poll++) {
            List<String> a = normalise(stock.update()), b = normalise(fast.update());
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
        if (fast.allocatedCountArrays != 0) {
            System.out.println("FAIL " + name + ": allocated " + fast.allocatedCountArrays
                    + " skip-snapshot count arrays with the skip disabled");
            failures++; return;
        }
        if (fast.allocatedProtoArrays != 0) {
            System.out.println("FAIL " + name + ": allocated " + fast.allocatedProtoArrays
                    + " prototype arrays with the skip disabled and no prune due");
            failures++; return;
        }
        System.out.println("pass  " + name + "  (" + fast.hits + "/" + fast.lookups
                + " hits, no self-disable, no prune, zero snapshot arrays allocated)");
    }


    static void overflowRegression() {
        String name = "stability counter overflow boundary";
        Repository repo = new Repository(false);
        for (int i = 0; i < 200; i++) {
            Drawer d = new Drawer(100000, false, false);
            d.prototype = new Stack("stable_item_" + i, 0, null); d.count = 10 + i;
            repo.drawers.add(d);
        }
        FastCache fast = new FastCache(repo, false);
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
            System.out.println("FAIL " + name + ": expected the judgement to settle, state was " + fast.stability);
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
        FastCache fast = new FastCache(repoB, false);
        for (int i = 0; i < 8; i++) { stock.update(); fast.update(); }
        if (!fast.disabled) {
            System.out.println("FAIL " + name + ": expected the cache to give up on unstable identities");
            failures++; return;
        }

        int conversionsBefore = fast.conversions, copiesBefore = fast.templateCopies;
        int polls = 5;
        for (int i = 0; i < polls; i++) {
            List<String> a = normalise(stock.update()), b = normalise(fast.update());
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

    public static void main(String[] args) {
        overflowRegression();
        disabledFallbackRegression();
        largeStableRegression(512, 10);
        largeStableRegression(1000, 10);
        vendingRegression(1, 64);
        vendingRegression(5, 64);
        vendingRegression(100, 999);

        for (long seed = 1; seed <= 20; seed++) {
            run("skip=on  stable   seed" + seed, seed, true, false, 4000);
        }
        for (long seed = 1; seed <= 5; seed++) {
            run("skip=off stable   seed" + seed, seed, false, false, 4000);
            run("skip=on  unstable seed" + seed, seed, true, true, 4000);
        }
        String[] few = ITEMS;
        String[] many = new String[120];
        for (int i = 0; i < many.length; i++) many[i] = "churn_item_" + i;
        ITEMS = many;
        for (long seed = 1; seed <= 5; seed++) {
            run("skip=on  churn    seed" + seed, seed, true, false, 4000);
            run("skip=off churn    seed" + seed, seed, false, false, 4000);
        }
        ITEMS = few;
        System.out.println(failures == 0 ? "\nALL PASS" : "\n" + failures + " FAILURES");
        if (failures != 0) System.exit(1);
    }
}
