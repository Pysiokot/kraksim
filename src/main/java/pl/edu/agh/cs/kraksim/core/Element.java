package pl.edu.agh.cs.kraksim.core;

import pl.edu.agh.cs.kraksim.core.exceptions.ExtensionUnsupportedException;
import pl.edu.agh.cs.kraksim.core.exceptions.InvalidExtensionClassException;

/**
 * Base class for extensible core elements
 */
public abstract class Element {
	protected final Core core;
	/**
	 * An array of extensions for this element. First
	 * core.moduleCount cells are filled, the rest are
	 * nulls.
	 */
	private Object[] extensions;
	private static final int INITIAL_EXTENSIONS_LENGTH = 4;

	protected Element(Core core) {
		this.core = core;
		extensions = new Object[INITIAL_EXTENSIONS_LENGTH];
	}

	/**
	 * Returns extension class for this type of element in module.
	 * NULL.class means there is no extension for this element.
	 */
	@SuppressWarnings("unchecked")
	protected abstract Class getExtensionClass(Module module);

	/**
	 * Returns extension object for this element in the given module.
	 */
	public final Object getExtension(final Module module) {
		return extensions[module.key];
	}

	/**
	 * Sets extension for this element in module to ext.
	 * <p/>
	 * The class of ext must satisfy the contract defined
	 * in Core.newModule()
	 * <p/>
	 * Throws ExtensionUnsupportedException if the contract says
	 * NULL is the class for extensions of this element.
	 * <p/>
	 * Throws InvalidExtensionClassException if ext is not an
	 * instance of class (nor its subclasses) for extensions
	 * of this type of element specified in the contract.
	 */
	@SuppressWarnings("unchecked")
	public final void setExtension(Module module, Object ext) throws ExtensionUnsupportedException, InvalidExtensionClassException {
		if (ext == null) {
			extensions[module.key] = null;
			return;
		}

		Class extClass = getExtensionClass(module);
		if (extClass == NULL.class) {
			throw new ExtensionUnsupportedException(String.format("Element: %s, module: %s", this, module.getName()));
		}
		if (!extClass.isInstance(ext)) {
			throw new InvalidExtensionClassException(String.format("Expected: %s, got: %s", extClass, ext.getClass()));
		}
		extensions[module.key] = ext;
	}

	/**
	 * Fired when new module is created.
	 */
	final void fireNewExtension() {
		int c = core.getModuleCount();
		if (c > extensions.length) {
			int m = Math.max(extensions.length, 1);
			while (m < c) {
				m *= 2;
			}
			resizeExtensions(m);
		}
	}

	/* Fired when module is popped. */
	final void firePopExtension() {
		extensions[core.getModuleCount()] = null;
	}

	/* Fired when modules are packed. */
	final void firePackExtensions() {
		resizeExtensions(core.getModuleCount());
	}

	private void resizeExtensions(int m) {
		Object[] newExtensions = new Object[m];
		System.arraycopy(extensions, 0, newExtensions, 0, Math.min(extensions.length, m));
		extensions = newExtensions;
	}
}
