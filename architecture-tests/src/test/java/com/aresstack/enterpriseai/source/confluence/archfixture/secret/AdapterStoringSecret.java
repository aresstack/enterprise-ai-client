package com.aresstack.enterpriseai.source.confluence.archfixture.secret;

import com.aresstack.enterpriseai.security.api.archfixture.secret.FakeSecretMaterial;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Absichtliche Verstöße: ein erlaubter Adapter hält Secret-Material über den Aufruf hinaus. */
public final class AdapterStoringSecret {

    private FakeSecretMaterial cachedLogin;
    private List<FakeSecretMaterial> cachedList;
    private final Map<String, FakeSecretMaterial> cachedBySpace = new HashMap<String, FakeSecretMaterial>();
    private FakeSecretMaterial[][] cachedArray;

    public void remember(FakeSecretMaterial material, List<FakeSecretMaterial> list, FakeSecretMaterial[][] array) {
        this.cachedLogin = material;
        this.cachedList = list;
        this.cachedArray = array;
        cachedBySpace.put("space", material);
    }

    public boolean loggedIn() {
        return cachedLogin != null && cachedList != null && cachedArray != null;
    }
}
