package test;

class Multicatch {
    void test() {
        try {
            // whatever
        } catch (ClassNotFoundException
                | ClassCastException
                | NoSuchMethodException
                | IllegalAccessException
                | InvocationTargetException e) {
            // ignore
        }
    }
}
