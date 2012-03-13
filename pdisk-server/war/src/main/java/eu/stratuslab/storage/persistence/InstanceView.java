package eu.stratuslab.storage.persistence;

import java.util.List;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;

@Root(name = "item")
public class InstanceView {

    @Attribute
    private String vmId;

	private String owner;

    public InstanceView(String vmId, String owner) {
    	this.vmId = vmId;
    	this.owner = owner;
    }

    @Root(name = "list")
    public static class InstanceViewList {

        @SuppressWarnings("unused")
        @ElementList(inline = true, required = false)
        private final List<InstanceView> list;

        public InstanceViewList(List<InstanceView> list) {
            this.list = list;
        }
    }

	public String getOwner() {
		return owner;
	}

	public void setOwner(String owner) {
		this.owner = owner;
	}

	public String getVmId() {
		return vmId;
	}

}
