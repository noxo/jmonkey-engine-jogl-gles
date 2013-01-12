package com.jme3.scene.plugins.blender.constraints.definitions;

import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import com.jme3.scene.plugins.blender.BlenderContext;
import com.jme3.scene.plugins.blender.file.Structure;

/**
 * This class represents 'Dist limit' constraint type in blender.
 * @author Marcin Roguski (Kaelthas)
 */
/*package*/ class ConstraintDefinitionDistLimit extends ConstraintDefinition {
	private static final int LIMITDIST_INSIDE = 0;
	private static final int LIMITDIST_OUTSIDE = 1;
	private static final int LIMITDIST_ONSURFACE = 2;
    
	protected int mode;
	protected float dist;
	
	public ConstraintDefinitionDistLimit(Structure constraintData, BlenderContext blenderContext) {
		super(constraintData, blenderContext);
		mode = ((Number) constraintData.getFieldValue("mode")).intValue();
		dist = ((Number) constraintData.getFieldValue("dist")).floatValue();
	}
	
	@Override
	public void bake(Transform ownerTransform, Transform targetTransform, float influence) {
		Vector3f v = ownerTransform.getTranslation().subtract(targetTransform.getTranslation());
		float currentDistance = v.length();
		
		switch (mode) {
			case LIMITDIST_INSIDE:
				if (currentDistance >= dist) {
					v.normalizeLocal();
					v.multLocal(dist + (currentDistance - dist) * (1.0f - influence));
					ownerTransform.getTranslation().set(v.addLocal(targetTransform.getTranslation()));
				}
				break;
			case LIMITDIST_ONSURFACE:
				if (currentDistance > dist) {
					v.normalizeLocal();
					v.multLocal(dist + (currentDistance - dist) * (1.0f - influence));
					ownerTransform.getTranslation().set(v.addLocal(targetTransform.getTranslation()));
				} else if(currentDistance < dist) {
					v.normalizeLocal().multLocal(dist * influence);
					ownerTransform.getTranslation().set(targetTransform.getTranslation().add(v));
				}
				break;
			case LIMITDIST_OUTSIDE:
				if (currentDistance <= dist) {
					v = targetTransform.getTranslation().subtract(ownerTransform.getTranslation()).normalizeLocal().multLocal(dist * influence);
					ownerTransform.getTranslation().set(targetTransform.getTranslation().add(v));
				}
				break;
			default:
				throw new IllegalStateException("Unknown distance limit constraint mode: " + mode);
		}
	}
}
