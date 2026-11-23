package aigest

import groovy.transform.CompileStatic

@CompileStatic
final class Version {

    public static final String NUMBER = '0.1.1'
    public static final String NAME = 'aigest'

    private Version() { }

    static String banner() {
        "${NAME} v${NUMBER}"
    }

}
