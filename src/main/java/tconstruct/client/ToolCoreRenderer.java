package tconstruct.client;

import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Icon;
import net.minecraftforge.client.IItemRenderer;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import tconstruct.TConstruct;
import tconstruct.library.tools.ToolCore;

public class ToolCoreRenderer implements IItemRenderer
{
    private boolean isEntity;
    
    public ToolCoreRenderer(boolean entity)
    {
        this.isEntity = entity;
    }
    
    @Override
    public boolean handleRenderType (ItemStack item, ItemRenderType type)
    {
        if (!item.hasTagCompound())
            return false;
        switch (type)
        {
        case ENTITY:
            GL11.glTranslatef(-0.0625F, -0.0625F, 0F);
            return true;
        case EQUIPPED:
            GL11.glTranslatef(0.03f, 0F, -0.09375F);
        case EQUIPPED_FIRST_PERSON:
            return !isEntity;
        case INVENTORY:
            return true;
        default:
            TConstruct.logger.warning("Unhandled render case!");
        case FIRST_PERSON_MAP:
            return false;
        }
    }

    @Override
    public boolean shouldUseRenderHelper (ItemRenderType type, ItemStack item, ItemRendererHelper helper)
    {
        return handleRenderType(item, type) & helper.ordinal() < ItemRendererHelper.EQUIPPED_BLOCK.ordinal();
    }

    private static final int toolIcons = 10;

    @Override
    public void renderItem (ItemRenderType type, ItemStack item, Object... data)
    {
        ToolCore tool = (ToolCore) item.getItem();

        boolean isInventory = type == ItemRenderType.INVENTORY;
        Entity ent = null;
        if (data.length > 1)
            ent = (Entity) data[1];

        int iconParts = toolIcons;//tool.getRenderPasses(item.getItemDamage());
        // TODO: have the tools define how many render passes they have
        // (requires more logic rewrite than it sounds like)

        Icon[] tempParts = new Icon[iconParts];
        label:
        {
            if (!isInventory && ent instanceof EntityPlayer)
            {
                EntityPlayer player = (EntityPlayer) ent;
                ItemStack itemInUse = player.getItemInUse();
                if (itemInUse != null)
                {
                    int useCount = player.getItemInUseCount();
                    for (int i = iconParts; i-- > 0;)
                        tempParts[i] = tool.getIcon(item, i, player, itemInUse, useCount);
                    break label;
                }
            }
            for (int i = iconParts; i-- > 0;)
                tempParts[i] = tool.getIcon(item, i);
        }

        int count = 0;
        Icon[] parts = new Icon[iconParts];
        for (int i = 0; i < iconParts; ++i)
        {
            Icon part = tempParts[i];
            if (part == null | part == ToolCore.blankSprite | part == ToolCore.emptyIcon)
                ++count;
            else
                parts[i - count] = part;
        }
        iconParts -= count;

        if (iconParts <= 0)
        {
            iconParts = 1;
            // TODO: assign default sprite
            // parts = new Icon[]{ defaultSprite };
        }

        Tessellator tess = Tessellator.instance;
        float[] xMax = new float[iconParts];
        float[] yMin = new float[iconParts];
        float[] xMin = new float[iconParts];
        float[] yMax = new float[iconParts];
        float depth = 1f / 16f;

        float[] width = new float[iconParts];
        float[] height = new float[iconParts];
        float[] xDiff = new float[iconParts];
        float[] yDiff = new float[iconParts];
        float[] xSub = new float[iconParts];
        float[] ySub = new float[iconParts];
        for (int i = 0; i < iconParts; ++i)
        {
            Icon icon = parts[i];
            xMin[i] = icon.getMinU();
            xMax[i] = icon.getMaxU();
            yMin[i] = icon.getMinV();
            yMax[i] = icon.getMaxV();
            width[i] = icon.getIconWidth();
            height[i] = icon.getIconHeight();
            xDiff[i] = xMin[i] - xMax[i];
            yDiff[i] = yMin[i] - yMax[i];
            xSub[i] = 0.5f * (xMax[i] - xMin[i]) / width[i];
            ySub[i] = 0.5f * (yMax[i] - yMin[i]) / height[i];
        }
        GL11.glPushMatrix();

        if (type == ItemRenderType.INVENTORY)
        {
            GL11.glDisable(GL11.GL_LIGHTING);
            tess.startDrawingQuads();
            for (int i = 0; i < iconParts; ++i)
            {
                tess.addVertexWithUV(0, 16, 0, xMin[i], yMax[i]);
                tess.addVertexWithUV(16, 16, 0, xMax[i], yMax[i]);
                tess.addVertexWithUV(16, 0, 0, xMax[i], yMin[i]);
                tess.addVertexWithUV(0, 0, 0, xMin[i], yMin[i]);
            }
            tess.draw();
            GL11.glEnable(GL11.GL_LIGHTING);
        }
        else
        {
            GL11.glEnable(GL12.GL_RESCALE_NORMAL);

            switch (type)
            {
            case EQUIPPED_FIRST_PERSON:
                break;
            case EQUIPPED:
                GL11.glTranslatef(0, -4 / 16f, 0);
                break;
            case ENTITY:
                GL11.glTranslatef(0, 4 / 16f, 0);
                break;
            default:
            }

            tess.startDrawingQuads();
            tess.setNormal(0, 0, 1);
            for (int i = 0; i < iconParts; ++i)
            {
                tess.addVertexWithUV(0, 0, 0, xMax[i], yMax[i]);
                tess.addVertexWithUV(1, 0, 0, xMin[i], yMax[i]);
                tess.addVertexWithUV(1, 1, 0, xMin[i], yMin[i]);
                tess.addVertexWithUV(0, 1, 0, xMax[i], yMin[i]);
            }
            tess.draw();
            tess.startDrawingQuads();
            tess.setNormal(0, 0, -1);
            for (int i = 0; i < iconParts; ++i)
            {
                tess.addVertexWithUV(0, 1, -depth, xMax[i], yMin[i]);
                tess.addVertexWithUV(1, 1, -depth, xMin[i], yMin[i]);
                tess.addVertexWithUV(1, 0, -depth, xMin[i], yMax[i]);
                tess.addVertexWithUV(0, 0, -depth, xMax[i], yMax[i]);
            }
            tess.draw();
            tess.startDrawingQuads();
            tess.setNormal(-1, 0, 0);
            float pos;
            float iconPos;

            for (int i = 0; i < iconParts; ++i)
            {
            	float w = width[i], m = xMax[i], d = xDiff[i], s = xSub[i];
                for (int k = 0, e = (int)w; k < e; ++k)
                {
                    pos = k / w;
                    iconPos = m + d * pos - s;
                    tess.addVertexWithUV(pos, 0, -depth, iconPos, yMax[i]);
                    tess.addVertexWithUV(pos, 0,      0, iconPos, yMax[i]);
                    tess.addVertexWithUV(pos, 1,      0, iconPos, yMin[i]);
                    tess.addVertexWithUV(pos, 1, -depth, iconPos, yMin[i]);
                }
            }

            tess.draw();
            tess.startDrawingQuads();
            tess.setNormal(1, 0, 0);
            float posEnd;

            for (int i = 0; i < iconParts; ++i)
            {
            	float w = width[i], m = xMax[i], d = xDiff[i], s = xSub[i];
            	float d2 = 1f / w;
            	for (int k = 0, e = (int)w; k < e; ++k)
                {
                    pos = k / w;
                    iconPos = m + d * pos - s;
                    posEnd = pos + d2;
                    tess.addVertexWithUV(posEnd, 1, -depth, iconPos, yMin[i]);
                    tess.addVertexWithUV(posEnd, 1,      0, iconPos, yMin[i]);
                    tess.addVertexWithUV(posEnd, 0,      0, iconPos, yMax[i]);
                    tess.addVertexWithUV(posEnd, 0, -depth, iconPos, yMax[i]);
                }
            }

            tess.draw();
            tess.startDrawingQuads();
            tess.setNormal(0, 1, 0);

            for (int i = 0; i < iconParts; ++i)
            {
            	float h = height[i], m = yMax[i], d = yDiff[i], s = ySub[i];
            	float d2 = 1f / h;
            	for (int k = 0, e = (int)h; k < e; ++k)
                {
                    pos = k / h;
                    iconPos = m + d * pos - s;
                    posEnd = pos + d2;
                    tess.addVertexWithUV(0, posEnd,      0, xMax[i], iconPos);
                    tess.addVertexWithUV(1, posEnd,      0, xMin[i], iconPos);
                    tess.addVertexWithUV(1, posEnd, -depth, xMin[i], iconPos);
                    tess.addVertexWithUV(0, posEnd, -depth, xMax[i], iconPos);
                }
            }

            tess.draw();
            tess.startDrawingQuads();
            tess.setNormal(0, -1, 0);

            for (int i = 0; i < iconParts; ++i)
            {
            	float h = height[i], m = yMax[i], d = yDiff[i], s = ySub[i];
            	for (int k = 0, e = (int)h; k < e; ++k)
                {
                    pos = k / h;
                    iconPos = m + d * pos - s;
                    tess.addVertexWithUV(1, pos,      0, xMin[i], iconPos);
                    tess.addVertexWithUV(0, pos,      0, xMax[i], iconPos);
                    tess.addVertexWithUV(0, pos, -depth, xMax[i], iconPos);
                    tess.addVertexWithUV(1, pos, -depth, xMin[i], iconPos);
                }
            }

            tess.draw();
            GL11.glDisable(GL12.GL_RESCALE_NORMAL);
        }

        GL11.glPopMatrix();
    }
}
